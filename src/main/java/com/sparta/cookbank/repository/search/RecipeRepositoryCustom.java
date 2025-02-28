
package com.sparta.cookbank.repository.search;

import com.sparta.cookbank.domain.recipe.Recipe;
import com.sparta.cookbank.domain.recipe.dto.RecipeRecommendRequestDto;
import com.sparta.cookbank.domain.recipe.dto.RecipeSearchRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RecipeRepositoryCustom {
    Page<Recipe> findBySearchOption(RecipeSearchRequestDto searchRequestDto, Pageable pageable);

    List<Recipe> findByRecommendRecipeOption(String baseName);
}
