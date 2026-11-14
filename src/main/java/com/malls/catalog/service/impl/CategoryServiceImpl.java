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
