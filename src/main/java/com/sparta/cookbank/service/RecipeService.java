package com.sparta.cookbank.service;

import com.sparta.cookbank.domain.LikeRecipe;
import com.sparta.cookbank.domain.member.Member;
import com.sparta.cookbank.domain.recipe.Recipe;
import com.sparta.cookbank.domain.recipe.dto.*;
import com.sparta.cookbank.repository.LikeRecipeRepository;
import com.sparta.cookbank.repository.MemberRepository;
import com.sparta.cookbank.repository.RecipeRepository;
import com.sparta.cookbank.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final LikeRecipeRepository likeRecipeRepository;
    private final MemberRepository memberRepository;

    // 추천 레시피 조회
    // TODO: 수정 필요
    @Transactional(readOnly = true)
    public List<RecipeRecommendResponseDto> getRecommendRecipe(RecipeRecommendRequestDto requestDto) {
        List<Recipe> recipeList  = recipeRepository.findAll();
        List<RecipeRecommendResponseDto> recipeRecommendResponseDtoList = new ArrayList<>();
        System.out.println(requestDto.getFoods());
        for (Recipe recipe : recipeList) {
            // 레시피의 재료에 소고기가 있으면
            if (recipe.getRCP_PARTS_DTLS().contains(requestDto.getBase())) {
                for (int i = 0; i < requestDto.getFoods().size(); i++) {
                    if (recipe.getRCP_PARTS_DTLS().contains(requestDto.getFoods().get(i))) {
                        recipeRecommendResponseDtoList.add(
                                RecipeRecommendResponseDto.builder()
                                        .id(recipe.getId())
                                        .recipe_name(recipe.getRCP_NM())
                                        .ingredients(recipe.getRCP_PARTS_DTLS())
                                        .method(recipe.getRCP_WAY2())
                                        .category(recipe.getRCP_PAT2())
                                        .calorie(recipe.getINFO_ENG())
                                        .build()
                        );
                    } else {
                        recipeRecommendResponseDtoList.add(
                                RecipeRecommendResponseDto.builder()
                                        .id(recipe.getId())
                                        .recipe_name(recipe.getRCP_NM())
                                        .ingredients(recipe.getRCP_PARTS_DTLS())
                                        .method(recipe.getRCP_WAY2())
                                        .category(recipe.getRCP_PAT2())
                                        .calorie(recipe.getINFO_ENG())
                                        .build()
                        );
                    }
                }

            }
        }
        return recipeRecommendResponseDtoList;
    }

    // 레시피 상세 조회
    @Transactional(readOnly = true)
    public RecipeDetailResultResponseDto getDetailRecipe(Long id) {

        Recipe recipe = recipeRepository.findById(id).orElseThrow(() -> {
            throw new IllegalArgumentException("해당 레시피가 존재하지 않습니다.");
        });

        // 재료들을 리스트에 담음
        List<String> ingredientsList = new ArrayList<>();
        ingredientsList.add(recipe.getRCP_PARTS_DTLS());

        // 방법들을 리스트에 담음
        List<String> manualDescList = new ArrayList<>();
        manualDescList.add(recipe.getMANUAL01());
        manualDescList.add(recipe.getMANUAL02());
        manualDescList.add(recipe.getMANUAL03());
        manualDescList.add(recipe.getMANUAL04());
        manualDescList.add(recipe.getMANUAL05());
        manualDescList.add(recipe.getMANUAL06());

        // 방법의 이미지들을 리스트에 담음
        List<String> manualImgList = new ArrayList<>();
        manualImgList.add(recipe.getMANUAL_IMG01());
        manualImgList.add(recipe.getMANUAL_IMG02());
        manualImgList.add(recipe.getMANUAL_IMG03());
        manualImgList.add(recipe.getMANUAL_IMG04());
        manualImgList.add(recipe.getMANUAL_IMG05());
        manualImgList.add(recipe.getMANUAL_IMG06());

        RecipeDetailResponseDto detailResponseDto = RecipeDetailResponseDto.builder()
                .id(id)
                .recipe_name(recipe.getRCP_NM())
                .ingredients(ingredientsList)
                .method(recipe.getRCP_WAY2())
                .category(recipe.getRCP_PAT2())
                .calorie(recipe.getINFO_ENG())
                .calbohydrates(recipe.getINFO_CAR())
                .proteins(recipe.getINFO_PRO())
                .fats(recipe.getINFO_FAT())
                .sodium(recipe.getINFO_NA())
                .final_img(recipe.getATT_FILE_NO_MK())
                .manual_desc(manualDescList)
                .manual_imgs(manualImgList)
                .build();

        RecipeDetailResultResponseDto resultResponseDto = RecipeDetailResultResponseDto.builder()
                .recipe(detailResponseDto)
                .build();

        return resultResponseDto;
    }

    // 레시피 전체 조회
    @Transactional(readOnly = true)
    public RecipeResponseDto getAllRecipe(Pageable pageable) {
        Page<Recipe> recipePage = recipeRepository.findAll(pageable);
        List<RecipeAllResponseDto> recipeAllResponseDtoList = new ArrayList<>();

        for (Recipe recipe : recipePage) {
            List<String> ingredientsList = new ArrayList<>();
            ingredientsList.add(recipe.getRCP_PARTS_DTLS());
            recipeAllResponseDtoList.add(
                    RecipeAllResponseDto.builder()
                            .id(recipe.getId())
                            .recipe_name(recipe.getRCP_NM())
                            .ingredients(ingredientsList)
                            .final_img(recipe.getATT_FILE_NO_MK())
                            .method(recipe.getRCP_WAY2())
                            .category(recipe.getRCP_PAT2())
                            .calorie(recipe.getINFO_ENG())
                            .build()
            );
        }

        RecipeResponseDto recipeResponseDto = RecipeResponseDto.builder()
                .current_page_num(recipePage.getPageable().getPageNumber())
                .total_page_num(recipePage.getTotalPages())
                .recipes(recipeAllResponseDtoList)
                .build();

        return recipeResponseDto;
    }

    // 레시피 검색
    @Transactional(readOnly = true)
    public RecipeResponseDto searchRecipe(RecipeSearchRequestDto searchRequestDto, Pageable pageable) {

        Page<Recipe> recipePage = recipeRepository.findBySearchOption(searchRequestDto,pageable);
        List<RecipeAllResponseDto> recipeAllResponseDtoList = new ArrayList<>();
        List<String> ingredientsList = new ArrayList<>();
        for (Recipe recipe : recipePage){
            ingredientsList.add(recipe.getRCP_PARTS_DTLS());
            recipeAllResponseDtoList.add(
                    RecipeAllResponseDto.builder()
                            .id(recipe.getId())
                            .recipe_name(recipe.getRCP_NM())
                            .ingredients(ingredientsList)
                            .final_img(recipe.getATT_FILE_NO_MK())
                            .method(recipe.getRCP_WAY2())
                            .category(recipe.getRCP_PAT2())
                            .calorie(recipe.getINFO_ENG())
                            .build()
            );
        }
        RecipeResponseDto recipeResponseDto = RecipeResponseDto.builder()
                .current_page_num(recipePage.getPageable().getPageNumber())
                .total_page_num(recipePage.getTotalPages())
                .recipes(recipeAllResponseDtoList)
                .build();

        return recipeResponseDto;
    }

    // 북마크 On
    @Transactional
    public void likeRecipe(Long id) {
        Member member = memberRepository.findById(SecurityUtil.getCurrentMemberId()).orElseThrow(() -> {
            throw new IllegalArgumentException("로그인한 유저를 찾을 수 없습니다.");
        });
        Recipe recipe = recipeRepository.findById(id).orElseThrow(() -> {
            throw  new IllegalArgumentException("해당 레시피를 찾을 수 없습니다.");
        });
        LikeRecipe likeRecipe1 = likeRecipeRepository.findByMember_IdAndRecipe_Id(member.getId(), recipe.getId());
        if (!(likeRecipe1 == null)) {
            throw new IllegalArgumentException("이미 북마크된 레시피 입니다.");
        }
        LikeRecipe likeRecipe = LikeRecipe.builder()
                .member(member)
                .recipe(recipe)
                .build();
        likeRecipeRepository.save(likeRecipe);
    }

    // 북마크 Off
    @Transactional
    public void unlikeRecipe(Long id) {
        Member member = memberRepository.findById(SecurityUtil.getCurrentMemberId()).orElseThrow(() -> {
            throw new IllegalArgumentException("로그인한 유저를 찾을 수 없습니다.");
        });
        Recipe recipe = recipeRepository.findById(id).orElseThrow(() -> {
            throw  new IllegalArgumentException("해당 레시피를 찾을 수 없습니다.");
        });
        LikeRecipe likeRecipe = likeRecipeRepository.findByMember_IdAndRecipe_Id(member.getId(), recipe.getId());
        if (likeRecipe == null) {
            throw new IllegalArgumentException("이미 삭제한 레시피입니다.");
        }
        likeRecipeRepository.delete(likeRecipe);
    }
}