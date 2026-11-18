package com.malls.bootstrap;

import com.malls.ai.domain.KnowledgeChunk;
import com.malls.ai.domain.KnowledgeDoc;
import com.malls.ai.domain.DocStatus;
import com.malls.ai.mapper.KnowledgeChunkMapper;
import com.malls.ai.mapper.KnowledgeDocMapper;
import com.malls.catalog.domain.Brand;
import com.malls.catalog.domain.Category;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.Sku;
import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.mapper.BrandMapper;
import com.malls.catalog.mapper.CategoryMapper;
import com.malls.catalog.mapper.ProductMapper;
import com.malls.catalog.mapper.SkuMapper;
import com.malls.marketing.domain.CouponTemplate;
import com.malls.marketing.domain.FullReduction;
import com.malls.marketing.domain.Seckill;
import com.malls.marketing.domain.CouponTemplateStatus;
import com.malls.marketing.domain.CouponType;
import com.malls.marketing.domain.SeckillStatus;
import com.malls.marketing.mapper.CouponTemplateMapper;
import com.malls.marketing.mapper.FullReductionMapper;
import com.malls.marketing.mapper.SeckillMapper;
import com.malls.user.domain.UserAccount;
import com.malls.user.domain.UserAddress;
import com.malls.user.domain.AccountStatus;
import com.malls.user.domain.Role;
import com.malls.user.mapper.UserAccountMapper;
import com.malls.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 演示数据初始化。
 *
 * <p>三条设计约束（文档 7 节）：
 * <ul>
 *   <li><b>幂等</b>：{@code selectCount > 0} 直接跳过，重启不会重复插数据</li>
 *   <li><b>密码 BCrypt</b>：种子数据也不存明文，演示账号密码统一 123456</li>
 *   <li><b>知识库同步切片</b>：3 篇文档启动即就绪，保证第一次问 AI 就有资料可检索</li>
 * </ul>
 *
 * <p>运行前必须先执行 {@code sql/schema.sql}，否则这里会因为表不存在而启动失败——
 * 这是有意的：宁可明确报错，也不要静默跳过让人以为数据初始化成功了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    /** 所有演示账号的统一密码 */
    private static final String DEMO_PASSWORD = "123456";

    private final UserAccountMapper userAccountMapper;
    private final UserAddressMapper userAddressMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final FullReductionMapper fullReductionMapper;
    private final SeckillMapper seckillMapper;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        if (userAccountMapper.selectCount(null) > 0) {
            log.info("检测到已有数据，跳过演示数据初始化");
            return;
        }

        UserAccount admin = seedUser("admin", "13800000001", "平台管理员", Role.ADMIN);
        seedUser("operator", "13800000002", "运营小王", Role.OPERATOR);
        seedUser("service", "13800000003", "客服小林", Role.SERVICE);
        UserAccount customer = seedUser("customer", "13800000004", "购物达人", Role.CUSTOMER);
        seedAddress(customer);

        List<Category> level1 = seedLevel1Categories();
        List<Category> level2 = seedLevel2Categories(level1);
        List<Brand> brands = seedBrands();
        List<Sku> skus = seedProducts(level1, level2, brands);
        seedMarketing(skus);
        seedKnowledge();

        log.info("演示数据初始化完成：用户 4、分类 6、品牌 3、商品 6、SKU {}、券 2、满减 1、秒杀 1、知识库 3",
                skus.size());
        log.info("演示账号 admin / operator / service / customer，统一密码 {}", DEMO_PASSWORD);
        log.info("管理员 ID={}，购物达人 ID={}", admin.getId(), customer.getId());
    }

    // ------------------------------------------------------------------ 用户

    private UserAccount seedUser(String username, String phone, String nickname, Role role) {
        var user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setPhone(phone);
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(AccountStatus.ENABLED);
        user.setPoints(role == Role.CUSTOMER ? 500 : 0);
        user.setLevel(role == Role.CUSTOMER ? 2 : 1);
        user.setTotalConsumption(role == Role.CUSTOMER ? new BigDecimal("1268.00") : BigDecimal.ZERO);
        userAccountMapper.insert(user);
        return user;
    }

    private void seedAddress(UserAccount customer) {
        var address = new UserAddress();
        address.setUserId(customer.getId());
        address.setReceiver("张三");
        address.setPhone(customer.getPhone());
        address.setProvince("湖北省");
        address.setCity("武汉市");
        address.setDistrict("洪山区");
        address.setDetail("珞喻路 100 号 武汉商学院 学生公寓 3 栋 502");
        address.setIsDefault(1);
        userAddressMapper.insert(address);
    }

    // ------------------------------------------------------------------ 商品

    private List<Category> seedLevel1Categories() {
        List<Category> list = new ArrayList<>();
        // 三个都是一级分类：level 必须都是 1，只有 sort 递增
        list.add(seedCategory(0L, "数码家电", 1, 10));
        list.add(seedCategory(0L, "服饰鞋包", 1, 20));
        list.add(seedCategory(0L, "家居生活", 1, 30));
        return list;
    }

    private List<Category> seedLevel2Categories(List<Category> level1) {
        List<Category> list = new ArrayList<>();
        list.add(seedCategory(level1.get(0).getId(), "手机通讯", 2, 11));
        list.add(seedCategory(level1.get(0).getId(), "电脑办公", 2, 12));
        list.add(seedCategory(level1.get(1).getId(), "运动鞋靴", 2, 21));
        return list;
    }

    private Category seedCategory(Long parentId, String name, int level, int sort) {
        var category = new Category();
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setSort(sort);
        categoryMapper.insert(category);
        return category;
    }

    private List<Brand> seedBrands() {
        List<Brand> list = new ArrayList<>();
        list.add(seedBrand("星辰 STAR", "年轻人数码品牌，主打性价比"));
        list.add(seedBrand("峰鸟 PEAK", "专业运动装备品牌"));
        list.add(seedBrand("云居 CLOUD", "家居生活美学品牌"));
        return list;
    }

    private Brand seedBrand(String name, String description) {
        var brand = new Brand();
        brand.setName(name);
        brand.setDescription(description);
        brandMapper.insert(brand);
        return brand;
    }

    /**
     * 6 个 SPU / 7 个 SKU。手机给两个 SKU 是为了演示"一 SPU 多 SKU"与最低价冗余。
     */
    private List<Sku> seedProducts(List<Category> level1, List<Category> level2, List<Brand> brands) {
        Long phoneCategory = level2.get(0).getId();
        Long laptopCategory = level2.get(1).getId();
        Long shoeCategory = level2.get(2).getId();
        Long apparelCategory = level1.get(1).getId();
        Long homeCategory = level1.get(2).getId();

        List<Sku> allSkus = new ArrayList<>();

        Product phone = seedProduct(phoneCategory, brands.get(0).getId(), "星辰 X1 5G 手机",
                "骁龙 8 系 · 120Hz 高刷 · 5000mAh 长续航", 128);
        allSkus.add(seedSku(phone, "8GB+128GB 曜石黑", "2999.00", 500));
        allSkus.add(seedSku(phone, "12GB+256GB 星河银", "3499.00", 300));

        Product laptop = seedProduct(laptopCategory, brands.get(0).getId(), "星辰 Book Pro 14",
                "14 英寸 2.8K 屏 · 32GB 内存 · 1TB 固态", 56);
        allSkus.add(seedSku(laptop, "32GB+1TB 深空灰", "6999.00", 80));

        Product shoe = seedProduct(shoeCategory, brands.get(1).getId(), "峰鸟 Air 缓震跑鞋",
                "全掌回弹中底 · 单只仅 215g", 342);
        allSkus.add(seedSku(shoe, "42 码 碳黑", "399.00", 200));

        Product tee = seedProduct(apparelCategory, brands.get(1).getId(), "峰鸟 速干短袖 T 恤",
                "冰感面料 · 运动速干", 890);
        allSkus.add(seedSku(tee, "L 码 雾霾蓝", "129.00", 1000));

        Product pillow = seedProduct(homeCategory, brands.get(2).getId(), "云居 天然乳胶枕",
                "泰国进口乳胶 · 93% 含量", 45);
        allSkus.add(seedSku(pillow, "标准款 60×40cm", "199.00", 260));

        Product aroma = seedProduct(homeCategory, brands.get(2).getId(), "云居 静音香薰机",
                "超声波雾化 · 缺水自动断电", 67);
        allSkus.add(seedSku(aroma, "300ml 米白", "259.00", 150));

        return allSkus;
    }

    private Product seedProduct(Long categoryId, Long brandId, String title, String subtitle, int sales) {
        var product = new Product();
        product.setCategoryId(categoryId);
        product.setBrandId(brandId);
        product.setTitle(title);
        product.setSubtitle(subtitle);
        product.setMainImage("/img/product/" + Math.abs(title.hashCode()) + ".jpg");
        product.setDetail("<p>" + subtitle + "</p>");
        product.setStatus(ProductStatus.ON_SALE);
        product.setSales(sales);
        product.setRating(new BigDecimal("4.80"));
        product.setReviewCount(sales / 10);
        // price 先占位，插完 SKU 后由 refreshProductPrice 回填最低价
        product.setPrice(BigDecimal.ZERO);
        productMapper.insert(product);
        return product;
    }

    private Sku seedSku(Product product, String spec, String price, int stock) {
        var sku = new Sku();
        sku.setProductId(product.getId());
        sku.setSpec(spec);
        sku.setPrice(new BigDecimal(price));
        sku.setStock(stock);
        sku.setEnabled(true);
        skuMapper.insert(sku);

        // 冗余最低价：列表页排序/筛选直接用 pms_product.price，不用每页联表聚合
        // 判据是"还没定价，或这个 SKU 更便宜"——注意不能只写 price > skuPrice，
        // 初始价为 0 时 0 > 2999 为 false，最低价永远回填不上
        boolean cheaperThanCurrent = product.getPrice().compareTo(BigDecimal.ZERO) == 0
                || sku.getPrice().compareTo(product.getPrice()) < 0;
        if (cheaperThanCurrent) {
            product.setPrice(sku.getPrice());
            productMapper.updateById(product);
        }
        return sku;
    }

    // ------------------------------------------------------------------ 营销

    private void seedMarketing(List<Sku> skus) {
        LocalDateTime now = LocalDateTime.now();

        seedCoupon("新人专享券 · 满 99 减 10", CouponType.FULL_REDUCTION,
                "99.00", "10.00", null, 10000, 3, now.minusDays(7), now.plusDays(30));
        seedCoupon("数码狂欢券 · 满 2000 减 200", CouponType.FULL_REDUCTION,
                "2000.00", "200.00", null, 500, 1, now.minusDays(3), now.plusDays(15));

        var reduction = new FullReduction();
        reduction.setName("全场满 300 减 30");
        reduction.setThresholdAmount(new BigDecimal("300.00"));
        reduction.setReduceAmount(new BigDecimal("30.00"));
        reduction.setStartTime(now.minusDays(1));
        reduction.setEndTime(now.plusDays(30));
        reduction.setStatus(CouponTemplateStatus.ACTIVE);
        fullReductionMapper.insert(reduction);

        // 秒杀第一条 SKU（手机的 8+128 版本），库存 100 件
        Sku target = skus.get(0);
        var seckill = new Seckill();
        seckill.setProductId(target.getProductId());
        seckill.setSkuId(target.getId());
        seckill.setSeckillPrice(new BigDecimal("2599.00"));
        seckill.setStock(100);
        seckill.setSold(0);
        seckill.setStartTime(now.minusHours(1));
        seckill.setEndTime(now.plusDays(7));
        seckill.setStatus(SeckillStatus.ACTIVE);
        seckillMapper.insert(seckill);
    }

    private void seedCoupon(String name, CouponType type, String threshold, String discount,
                            BigDecimal rate, int total, int perLimit,
                            LocalDateTime start, LocalDateTime end) {
        var template = new CouponTemplate();
        template.setName(name);
        template.setType(type);
        template.setThresholdAmount(new BigDecimal(threshold));
        template.setDiscountAmount(new BigDecimal(discount));
        template.setDiscountRate(rate);
        template.setTotalCount(total);
        template.setReceivedCount(0);
        template.setPerLimit(perLimit);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setStatus(CouponTemplateStatus.ACTIVE);
        couponTemplateMapper.insert(template);
    }

    // ------------------------------------------------------------------ 知识库

    /**
     * 3 篇知识文档，按空行切片写入 {@code ai_knowledge_chunk}。
     *
     * <p>Day09 接入 Spring AI 后会重新 ingest 生成向量（embedding 列），
     * 现在先留空——切片本身已经能支撑"检索 + 引用"的完整链路演示。
     */
    private void seedKnowledge() {
        seedDoc("配送与运费规则", "物流",
                """
                全国大部分地区单笔订单满 99 元包邮，未满 99 元收取 10 元运费。

                新疆、西藏、内蒙古部分地区不参与包邮活动，运费按实际重量与目的地计算。

                正常情况下单后 24 小时内发货，节假日顺延；发货后可在"我的订单"里查看物流轨迹。""");

        seedDoc("退换货政策", "售后",
                """
                支持 7 天无理由退货，商品需保持完好、配件齐全、不影响二次销售。

                生鲜食品、定制类商品、已激活的数码产品不支持无理由退货。

                退款在售后审核通过后 1 至 3 个工作日内按原支付方式退回。""");

        seedDoc("优惠券使用规则", "营销",
                """
                优惠券不可叠加使用，一个订单只能使用一张优惠券。

                优惠券需满足门槛金额才能使用，门槛按商品总额计算，不包含运费。

                订单取消后已使用的优惠券自动返还至账户，有效期保持不变。""");
    }

    private void seedDoc(String title, String category, String content) {
        String[] chunks = content.split("\\n\\s*\\n");

        var doc = new KnowledgeDoc();
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setContent(content);
        doc.setStatus(DocStatus.READY);
        doc.setChunkCount(chunks.length);
        knowledgeDocMapper.insert(doc);

        for (int i = 0; i < chunks.length; i++) {
            var chunk = new KnowledgeChunk();
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks[i].trim());
            knowledgeChunkMapper.insert(chunk);
        }
    }
}
