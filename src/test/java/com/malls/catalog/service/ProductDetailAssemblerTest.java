package com.malls.catalog.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.malls.catalog.domain.Brand;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.domain.Sku;
import com.malls.catalog.mapper.BrandMapper;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.catalog.vo.ProductDetailVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 详情取数的单元测试（Mock 全部 Mapper，不连数据库）。
 *
 * <p>重点锁住两件事：商品不存在时抛 {@code 12001} 而不是 NPE；
 * 只查 {@code enabled = true} 的 SKU 且按价格升序（前台只该看到可售规格）。
 */
@ExtendWith(MockitoExtension.class)
class ProductDetailAssemblerTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private BrandMapper brandMapper;

    @InjectMocks
    private ProductDetailAssembler assembler;

    @BeforeAll
    static void initTableInfo() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Product.class);
        TableInfoHelper.initTableInfo(assistant, Sku.class);
    }

    @Test
    @DisplayName("商品不存在：抛 12001，别再让 product.getBrandId() 打出空指针")
    void productNotFound() {
        when(productMapper.selectById(99L)).thenReturn(null);

        Throwable thrown = catchThrowable(() -> assembler.assemble(99L));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getCode()).isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
        verifyNoInteractions(skuMapper, brandMapper);
    }

    @Test
    @DisplayName("无品牌时品牌名回落「平台自营」，图集按逗号拆且丢空串")
    void selfOperatedWithoutBrand() {
        Product product = product(null, "a.jpg, ,b.jpg", ProductStatus.OFF_SHELF);
        when(productMapper.selectById(1L)).thenReturn(product);
        when(skuMapper.selectList(any())).thenReturn(List.of(sku(11L, "8GB+128GB", "1999", 5)));

        ProductDetailVO detail = assembler.assemble(1L);

        assertThat(detail.brandName()).isEqualTo("平台自营");
        assertThat(detail.images()).containsExactly("a.jpg", "b.jpg");
        assertThat(detail.status()).isEqualTo("OFF_SHELF");
        assertThat(detail.skus()).hasSize(1);
        verifyNoInteractions(brandMapper);
    }

    @Test
    @DisplayName("只查启用 SKU，并按价格升序取")
    @SuppressWarnings("unchecked")
    void onlyEnabledSkusOrderedByPrice() {
        Product product = product(2L, null, ProductStatus.ON_SALE);
        when(productMapper.selectById(1L)).thenReturn(product);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setName("小米");
        when(brandMapper.selectById(2L)).thenReturn(brand);
        when(skuMapper.selectList(any())).thenReturn(List.of(
                sku(12L, "12GB+256GB", "2999", 3),
                sku(11L, "8GB+128GB", "1999", 5)));

        ProductDetailVO detail = assembler.assemble(1L);

        assertThat(detail.brandName()).isEqualTo("小米");
        assertThat(detail.images()).isEmpty();

        ArgumentCaptor<Wrapper<Sku>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(skuMapper).selectList(captor.capture());
        String sql = ((LambdaQueryWrapper<Sku>) captor.getValue()).getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("enabled");
        assertThat(sql).contains("price asc");
    }

    private static Product product(Long brandId, String images, ProductStatus status) {
        Product product = new Product();
        product.setId(1L);
        product.setTitle("小米10");
        product.setBrandId(brandId);
        product.setImages(images);
        product.setPrice(new BigDecimal("1268.00"));
        product.setStatus(status);
        return product;
    }

    private static Sku sku(Long id, String spec, String price, int stock) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setProductId(1L);
        sku.setSpec(spec);
        sku.setPrice(new BigDecimal(price));
        sku.setStock(stock);
        sku.setEnabled(true);
        return sku;
    }
}
