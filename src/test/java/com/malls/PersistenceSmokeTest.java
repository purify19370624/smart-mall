package com.malls;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.malls.catalog.domain.Brand;
import com.malls.catalog.domain.Category;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.domain.Sku;
import com.malls.catalog.mapper.BrandMapper;
import com.malls.catalog.mapper.CategoryMapper;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.common.api.Result;
import com.malls.user.domain.UserAccount;
import com.malls.user.domain.UserAddress;
import com.malls.user.mapper.UserAccountMapper;
import com.malls.user.mapper.UserAddressMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

// 打开 MyBatis 的 SQL 打印：控制台能直接看到真执行的语句（分页插件会打印 COUNT(*) 和 LIMIT），
// 觉得刷屏就删掉 properties 这一段
@SpringBootTest(properties = {
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.stdout.StdOutImpl"
})
@Transactional
public class PersistenceSmokeTest {
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private UserAddressMapper userAddressMapper;
    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private BrandMapper brandMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TITLE = "按标题查询-字段映射校验";
    @Test
    @DisplayName("添加一个手机商品 定价1268    把这个商品添加到用户的地址  张三  湖北省  武汉市  洪山区  珞喻路 100 号 武汉商学院 学生公寓 3 栋 502  字段完整")
    @Commit
    public void add(){
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setName("小米");
        brand.setSort(2);
        brand.setDescription("性价比手机");
        brandMapper.insert(brand);
        Category category = new Category();
        category.setId(1L);
        category.setName("手机");
        categoryMapper.insert(category);
        Product product = new Product();
        product.setId(1L);
        product.setBrandId(1L);
        product.setCategoryId(1L);
        product.setTitle("小米10");
        product.setSubtitle("1");
        product.setPrice(new BigDecimal("1268"));
        productMapper.insert(product);
        UserAccount userAccount = new UserAccount();
        userAccount.setId(1L);
        userAccount.setUsername("张三");
        userAccount.setPassword("123456");
        userAccount.setPhone("13475432668");
        userAccountMapper.insert(userAccount);
        UserAddress userAddress = new UserAddress();
        userAddress.setUserId(1L);
        userAddress.setReceiver("张三");
        userAddress.setPhone("13475432668");
        userAddress.setProvince("湖北省");
        userAddress.setCity("武汉市");
        userAddress.setDistrict("洪山区");
        userAddress.setDetail("珞喻路 100 号 武汉商学院 学生公寓 3 栋 502");
        userAddressMapper.insert(userAddress);
    }

    @Test
    @DisplayName("实体字段与表列一一对应： 根据按标题查到商品")
    @Commit
    public void FindByTitle() {
        Product expected = new Product();
        expected.setCategoryId(1L);
        expected.setBrandId(1L);
        expected.setTitle(TITLE);
        expected.setSubtitle("副标题");
        expected.setMainImage("示例图片.jpg");
        expected.setImages("a.jpg,b.jpg");
        expected.setDetail("这是详情");
        expected.setAiSellingPoints("长续航");
        expected.setPrice(new BigDecimal("1268.00"));
        expected.setStatus(ProductStatus.ON_SALE);
        expected.setSales(7);
        expected.setRating(new BigDecimal("4.50"));
        expected.setReviewCount(3);
        productMapper.insert(expected);
        List<Product> found = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getTitle, TITLE));
        assertThat(found).hasSize(1);
        Product actual = found.get(0);
        System.out.printf("%n[按标题查询] title=%s，命中 %d 条，回读到的实体：%n", TITLE, found.size());
        dump(actual);
        assertThat(actual.getId()).isNotNull();
        assertThat(actual.getCategoryId()).isEqualTo(1L);
        assertThat(actual.getBrandId()).isEqualTo(1L);
        assertThat(actual.getTitle()).isEqualTo(TITLE);
        assertThat(actual.getSubtitle()).isEqualTo("副标题");
        assertThat(actual.getMainImage()).isEqualTo("示例图片.jpg");
        assertThat(actual.getImages()).isEqualTo("a.jpg,b.jpg");
        assertThat(actual.getDetail()).isEqualTo("这是详情");
        assertThat(actual.getAiSellingPoints()).isEqualTo("长续航");
        assertThat(actual.getPrice()).isEqualByComparingTo("1268.00");
        assertThat(actual.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(actual.getSales()).isEqualTo(7);
        assertThat(actual.getRating()).isEqualByComparingTo("4.50");
        assertThat(actual.getReviewCount()).isEqualTo(3);
        assertThat(actual.getCreateTime()).isNotNull();
        assertThat(actual.getUpdateTime()).isNotNull();
        assertThat(actual.getDeleted()).isNotNull().isZero();
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = database() and table_name = 'pms_product' "
                        + "order by ordinal_position",
                String.class);
        System.out.println("[列名对照] 库里的列   = " + columns);
        System.out.println("[列名对照] 实体的字段 = " + entityColumns(Product.class));
        assertThat(columns).containsExactlyInAnyOrderElementsOf(entityColumns(Product.class));
    }
    private static List<String> entityColumns(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                names.add(f.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
    @Test
    @DisplayName("添加sku表中的数据  sku表分页插件生效：第 1 页取 3 条，总数仍是 7")
    public void skuPage() {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setTitle("分页校验用商品");
        product.setPrice(new BigDecimal("0.00"));
        productMapper.insert(product);
        Long productId = product.getId();
        System.out.printf("%n[准备] 分页校验用商品 id=%s%n", productId);

        for (int i = 1; i <= 7; i++) {
            Sku sku = new Sku();
            sku.setProductId(productId);
            sku.setSpec("8GB+128GB 曜石黑 #" + i);
            sku.setPrice(new BigDecimal("1268.00").add(new BigDecimal(i)));
            sku.setStock(100 + i);
            sku.setImage("sku-" + i + ".jpg");
            sku.setEnabled(i % 2 == 0);          // true/false 混着来，顺便验证 bit(1) 存得住
            skuMapper.insert(sku);
            System.out.printf("[插入] sku id=%s spec=%s price=%s stock=%d enabled=%s%n",
                    sku.getId(), sku.getSpec(), sku.getPrice(), sku.getStock(), sku.isEnabled());
        }

        Page<Sku> result = skuMapper.selectPage(new Page<>(1, 3),
                new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getProductId, productId)
                        .orderByAsc(Sku::getId));


        System.out.println("── 第 1 页结果 ──────────────────────────");
        System.out.printf("current=%d  size=%d  total=%d  pages=%d  本次返回=%d 条%n",
                result.getCurrent(), result.getSize(), result.getTotal(),
                result.getPages(), result.getRecords().size());
        result.getRecords().forEach(s -> System.out.printf("  · id=%s spec=%s price=%s stock=%d enabled=%s%n",
                s.getId(), s.getSpec(), s.getPrice(), s.getStock(), s.isEnabled()));
        System.out.println("────────────────────────────────────────");


        assertThat(result.getRecords()).hasSize(3);
        assertThat(result.getTotal()).isEqualTo(7);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getCurrent()).isEqualTo(1);

        Page<Sku> page2 = skuMapper.selectPage(new Page<>(2, 3),
                new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getProductId, productId)
                        .orderByAsc(Sku::getId));
        System.out.printf("[第 2 页] 返回 %d 条，total 仍是 %d%n",
                page2.getRecords().size(), page2.getTotal());
        assertThat(page2.getRecords()).hasSize(3);
        assertThat(page2.getTotal()).isEqualTo(7);

        Page<Sku> page3 = skuMapper.selectPage(new Page<>(3, 3),
                new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getProductId, productId)
                        .orderByAsc(Sku::getId));
        System.out.printf("[第 3 页] 返回 %d 条，total 仍是 %d%n",
                page3.getRecords().size(), page3.getTotal());
        assertThat(page3.getRecords()).hasSize(1);
        assertThat(page3.getTotal()).isEqualTo(7);
    }


    private static void dump(Object bean) {
        for (Class<?> c = bean.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    System.out.printf("  %-18s = %s%n", f.getName(), f.get(bean));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("读取字段失败: " + f.getName(), e);
                }
            }
        }
    }

}
