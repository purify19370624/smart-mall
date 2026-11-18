package com.malls.catalog.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.dto.ProductQuery;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.catalog.service.CategoryService;
import com.malls.catalog.service.ProductDetailAssembler;
import com.malls.catalog.vo.ProductDetailVO;
import com.malls.catalog.vo.ProductVO;
import com.malls.common.api.PageResult;
import com.malls.common.api.ResultCode;
import com.malls.common.exception.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 后台商品管理的单元测试（Mock 持久层，不连数据库）。
 *
 * <p>覆盖三个最容易写错的地方：后台列表<b>没有</b>上架过滤、上下架<b>幂等且并发安全</b>、
 * 后台详情<b>直连取数组件</b>（将来前台加缓存也不影响）。
 */
@ExtendWith(MockitoExtension.class)
class AdminProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private ProductDetailAssembler productDetailAssembler;

    @InjectMocks
    private AdminProductServiceImpl adminProductService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Product.class);
    }

    @Test
    @DisplayName("上架成功：OFF_SHELF → ON_SALE，返回新状态")
    void onShelfSuccess() {
        when(productMapper.selectById(7L)).thenReturn(product(7L, ProductStatus.OFF_SHELF));
        when(skuMapper.selectCount(any())).thenReturn(2L);
        doReturn(1).when(productMapper).update(any(), any());

        ProductVO vo = adminProductService.onShelf(7L);

        assertThat(vo.id()).isEqualTo(7L);
        assertThat(vo.status()).isEqualTo(ProductStatus.ON_SALE.name());
        verify(productMapper).update(any(), any());
    }

    @Test
    @DisplayName("商品不存在：抛 12001")
    void onShelfMissingProduct() {
        Throwable thrown = catchThrowable(() -> adminProductService.onShelf(404L));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getCode()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("重复上架：幂等，不写库、不校验 SKU")
    void onShelfIsIdempotent() {
        when(productMapper.selectById(7L)).thenReturn(product(7L, ProductStatus.ON_SALE));

        ProductVO vo = adminProductService.onShelf(7L);

        assertThat(vo.status()).isEqualTo(ProductStatus.ON_SALE.name());
        verifyNoInteractions(skuMapper);
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("没有可用规格不允许上架：12005，状态不变")
    void onShelfWithoutSellableSku() {
        when(productMapper.selectById(7L)).thenReturn(product(7L, ProductStatus.OFF_SHELF));
        when(skuMapper.selectCount(any())).thenReturn(0L);

        Throwable thrown = catchThrowable(() -> adminProductService.onShelf(7L));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getCode())
                .isEqualTo(ResultCode.PRODUCT_STATUS_ILLEGAL.getCode());
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("下架成功：ON_SALE → OFF_SHELF，且不校验 SKU")
    void offShelfSuccess() {
        when(productMapper.selectById(7L)).thenReturn(product(7L, ProductStatus.ON_SALE));
        doReturn(1).when(productMapper).update(any(), any());

        ProductVO vo = adminProductService.offShelf(7L);

        assertThat(vo.status()).isEqualTo(ProductStatus.OFF_SHELF.name());
        verifyNoInteractions(skuMapper);
    }

    @Test
    @DisplayName("并发改状态：条件更新 0 行时回读真实状态，不回过期值")
    void concurrentChangeReadsActualStatus() {
        when(productMapper.selectById(7L))
                .thenReturn(product(7L, ProductStatus.OFF_SHELF))   // 先读到旧值
                .thenReturn(product(7L, ProductStatus.ON_SALE));    // 回读拿到并发结果
        when(skuMapper.selectCount(any())).thenReturn(1L);
        doReturn(0).when(productMapper).update(any(), any());

        ProductVO vo = adminProductService.onShelf(7L);

        assertThat(vo.status()).isEqualTo(ProductStatus.ON_SALE.name());
        verify(productMapper, times(2)).selectById(7L);
    }

    @Test
    @DisplayName("后台详情：直接调取数组件，下架商品也能看")
    void detailUsesAssemblerDirectly() {
        ProductDetailVO expected = ProductDetailVO.of(
                product(7L, ProductStatus.OFF_SHELF), null, "平台自营", List.of());
        when(productDetailAssembler.assemble(7L)).thenReturn(expected);

        ProductDetailVO actual = adminProductService.detail(7L);

        assertThat(actual).isSameAs(expected);
        verify(productDetailAssembler).assemble(7L);
    }

    @Test
    @DisplayName("后台列表：不加上架过滤，分类子树条件生效")
    @SuppressWarnings("unchecked")
    void pageHasNoOnSaleFilter() {
        when(categoryService.subtreeIds(9L)).thenReturn(List.of(9L, 10L));
        Page<Product> page = new Page<>(1, 12);
        page.setRecords(List.of(product(7L, ProductStatus.OFF_SHELF)));
        doReturn(page).when(productMapper).selectPage(any(), any());

        PageResult<ProductVO> result = adminProductService.page(
                new ProductQuery(null, 9L, null, null, null, null, 1, 12), null);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).status()).isEqualTo(ProductStatus.OFF_SHELF.name());

        ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(productMapper).selectPage(any(), captor.capture());
        String sql = ((LambdaQueryWrapper<Product>) captor.getValue()).getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).doesNotContain("status");
        assertThat(sql).contains("category_id");
    }

    @Test
    @DisplayName("后台列表：显式传 status 时才加状态条件")
    @SuppressWarnings("unchecked")
    void pageWithExplicitStatus() {
        Page<Product> page = new Page<>(1, 12);
        page.setRecords(List.of());
        doReturn(page).when(productMapper).selectPage(any(), any());

        adminProductService.page(
                new ProductQuery(null, null, null, null, null, null, 1, 12), ProductStatus.OFF_SHELF);

        ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(productMapper).selectPage(any(), captor.capture());
        String sql = ((LambdaQueryWrapper<Product>) captor.getValue()).getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("status");
    }

    private static Product product(Long id, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setTitle("小米10");
        product.setPrice(new BigDecimal("1268.00"));
        product.setStatus(status);
        return product;
    }
}
