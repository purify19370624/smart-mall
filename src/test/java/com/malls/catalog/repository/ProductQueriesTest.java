package com.malls.catalog.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.malls.catalog.domain.Product;
import com.malls.catalog.domain.ProductStatus;
import com.malls.catalog.dto.ProductQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索条件拼装的单元测试（不启动 Spring、不连数据库）。
 *
 * <p>为什么要在 {@link BeforeAll} 里手工登记表元数据：{@code LambdaQueryWrapper}
 * 靠实体元数据把 {@code Product::getStatus} 翻译成列名，平时由 MyBatis 启动时注册，
 * 纯单元测试里没有那一步，不登记就会报 {@code can not find lambda cache}。
 */
class ProductQueriesTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Product.class);
    }

    private static ProductQuery emptyQuery() {
        return new ProductQuery(null, null, null, null, null, null, null, null);
    }

    private static ProductQuery keywordQuery(String keyword) {
        return new ProductQuery(keyword, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("前台查询一定带上架过滤")
    void onSaleFilterPresent() {
        String sql = ProductQueries.ofQuery(emptyQuery(), null, true).getSqlSegment();
        assertThat(sql).contains("status");
    }

    @Test
    @DisplayName("后台查询不加上架过滤：运营才能看到下架商品")
    void adminQueryHasNoOnSaleFilter() {
        String sql = ProductQueries.ofAdminQuery(keywordQuery("手机"), null, null).getSqlSegment();
        assertThat(sql).contains("title");             // 关键词条件生效，证明 SQL 非空
        assertThat(sql).doesNotContain("status");      // 关键：没有 status 条件
    }

    @Test
    @DisplayName("后台按状态过滤时只追加等值条件")
    void adminQueryWithExplicitStatus() {
        String sql = ProductQueries.ofAdminQuery(emptyQuery(), null, ProductStatus.OFF_SHELF).getSqlSegment();
        assertThat(sql).contains("status");
    }

    @Test
    @DisplayName("关键词用 and(...) 包住 or 组，避免下架商品漏出")
    void keywordGroupIsParenthesised() {
        String sql = ProductQueries.ofQuery(keywordQuery("手机"), null, true).getSqlSegment();
        assertThat(sql).contains("(");
        assertThat(sql.toLowerCase(Locale.ROOT)).contains("or");
        assertThat(sql).contains("title");
    }

    @Test
    @DisplayName("排序白名单：合法值直接生效")
    void sortWhitelistApplied() {
        LambdaQueryWrapper<Product> wrapper = ProductQueries.ofQuery(emptyQuery(), null, true);
        ProductQueries.sorted("price_asc", wrapper);
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by price asc");
    }

    @Test
    @DisplayName("排序白名单：非法值回落默认序，绝不拼进 SQL")
    void illegalSortFallsBack() {
        LambdaQueryWrapper<Product> wrapper = ProductQueries.ofQuery(emptyQuery(), null, true);
        ProductQueries.sorted("drop table pms_product", wrapper);
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).doesNotContain("drop table");
        assertThat(sql).contains("order by");
    }

    @Test
    @DisplayName("分类集合为空时不加 in 条件（而不是 in ()）")
    void emptyCategoryIdsIgnored() {
        String sql = ProductQueries.ofQuery(emptyQuery(), List.of(), false).getSqlSegment();
        assertThat(sql).doesNotContain("category_id");
    }
}
