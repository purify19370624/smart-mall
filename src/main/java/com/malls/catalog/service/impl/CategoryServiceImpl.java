package com.malls.catalog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.malls.catalog.domain.Category;
import com.malls.catalog.mapper.CategoryMapper;
import com.malls.catalog.service.CategoryService;
import com.malls.catalog.vo.CategoryNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service                     // 注册为业务组件
@RequiredArgsConstructor     // 构造器注入
public class CategoryServiceImpl implements CategoryService {
    private  static  final  int MAX_DEPTH = 3;

    private  final CategoryMapper categoryMapper;

    @Override
    public List<CategoryNode> tree() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        List<CategoryNode> firstLabel = new ArrayList<>();
        for (Category category : categories) {
            if(isBoot(category)){
                firstLabel.add(buildNode(category,categories,1));
            }
        }


        return firstLabel;
    }

    /**
     * 取某分类及其所有子孙分类的 ID。
     *
     * <p>一次把整张分类表读进内存再递归，而不是每层发一次 SQL：
     * 分类是"读多写极少"的小表，一条查询换掉 N 次往返更划算。
     * 若数据量变大，改成递归 CTE 或把树缓存起来，接口不用变。
     */
    @Override
    public List<Long> subtreeIds(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        List<Category> all = categoryMapper.selectList(null);
        List<Long> result = new ArrayList<>();
        result.add(categoryId);
        collectChildren(categoryId, all, result);
        return result;
    }

    private void collectChildren(Long parentId, List<Category> all, List<Long> result) {
        for (Category category : all) {
            if (parentId.equals(category.getParentId())) {
                result.add(category.getId());
                collectChildren(category.getId(), all, result);
            }
        }
    }

    private CategoryNode buildNode(Category category, List<Category> categories, int level) {
        List<CategoryNode> children = new ArrayList<>();
        if(level<MAX_DEPTH){
            for (Category category1 : categories) {
                if(category.getId().equals(category1.getParentId())){
                    children.add(buildNode(category1,categories,level+1));
                }
            }
        }
        return CategoryNode.of(category,children);
    }

    private boolean isBoot(Category category) {
        return category.getParentId()==null||category.getParentId() == 0;
    }
}
