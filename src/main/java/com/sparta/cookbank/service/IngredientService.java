package com.sparta.cookbank.service;

import com.sparta.cookbank.ResponseDto;
import com.sparta.cookbank.domain.Storage;
import com.sparta.cookbank.domain.ingredient.Ingredient;
import com.sparta.cookbank.domain.ingredient.dto.*;
import com.sparta.cookbank.domain.member.Member;
import com.sparta.cookbank.domain.myingredients.MyIngredients;
import com.sparta.cookbank.domain.myingredients.dto.*;
import com.sparta.cookbank.redis.ingredient.RedisIngredientRepo;
import com.sparta.cookbank.repository.IngredientsRepository;
import com.sparta.cookbank.repository.MemberRepository;
import com.sparta.cookbank.repository.MyIngredientsRepository;
import com.sparta.cookbank.security.SecurityUtil;
import com.sparta.cookbank.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientsRepository ingredientsRepository;
    private final MemberRepository memberRepository;
    private final MyIngredientsRepository myIngredientsRepository;
    private final TokenProvider tokenProvider;

    private final RedisIngredientRepo redisIngredientRepo;

    @Transactional(readOnly = true)
    public ResponseDto<?> findAutoIngredient(String food_name, HttpServletRequest request) {

        // Token 유효성 검사 없음

        //해당 검색어 찾기
        List<Ingredient> ingredients = ingredientsRepository.findAllByFoodNameIsContaining(food_name);
        // DTO사용
        List<IngredientResponseDto> dtoList = new ArrayList<>();
        // 5개만 보여주기
        for(int i=0; i<5; i++){
            dtoList.add(IngredientResponseDto.builder()
                    .id(ingredients.get(i).getId())
                    .food_name(ingredients.get(i).getFoodName())
                    .group_name(ingredients.get(i).getFoodCategory())
                    .build());
        }

        AutoIngredientResponseDto responseDto = AutoIngredientResponseDto.builder()
                .auto_complete(dtoList)
                .build();


        return ResponseDto.success(responseDto,"자동완성 리스트 제공에 성공하였습니다.");
    }

    @Transactional(readOnly = true)
    public ResponseDto<?> findIngredient(String food_name, HttpServletRequest request) {

        // Token 유효성 검사 없음

        // Redis  찾기를 할때 ingredient db를 캐시에 저장한다면??


        //해당 검색 찾기
        List<Ingredient> ingredients = ingredientsRepository.findAllByFoodNameIsContaining(food_name);

        // DTO사용
        List<IngredientResponseDto> dtoList = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            dtoList.add(IngredientResponseDto.builder()
                    .id(ingredient.getId())
                    .food_name(ingredient.getFoodName())
                    .group_name(ingredient.getFoodCategory())
                    .build());
        }

        TotalIngredientResponseDto responseDto = TotalIngredientResponseDto.builder()
                .total_count(dtoList.size())
                .search_list(dtoList)
                .build();
        return ResponseDto.success(responseDto,"식재료 검색에 성공하였습니다.");
    }
    @Transactional
    public ResponseDto<?> saveMyIngredient(IngredientRequestDto requestDto, HttpServletRequest request) {

        //토큰 유효성 검사
        extracted(request);

        // 멤버 유효성 검사
        Member member = getMember();
        //재료찾기
        Ingredient ingredient = ingredientsRepository.findById(requestDto.getId()).orElseThrow(
                () -> new IllegalArgumentException("해당 음식 재료가 존재 하지 않습니다.")
        );

        MyIngredients myIngredients = MyIngredients.builder()
                .member(member)
                .ingredient(ingredient)
                .storage(requestDto.getStorage())
                .inDate(requestDto.getIn_date())
                .expDate(requestDto.getExp_date())
                .build();
        myIngredientsRepository.save(myIngredients);

        return ResponseDto.success("","작성완료");
    }


    @Transactional(readOnly = true)
    public ResponseDto<?> getMyIngredient(String storage, HttpServletRequest request) throws ParseException {
        //토큰 유효성 검사
        extracted(request);

        // 멤버 유효성 검사
        Member member = getMember();


        // 나의 재료 전체조회
        if(storage.equals("")){
            List<MyIngredients> myIngredients = myIngredientsRepository.findAllByMemberId(member.getId());
            List<MyIngredientResponseDto> dtoList = new ArrayList<>();
            StorageResponseDto responseDto = getStorageResponseDto(myIngredients, dtoList);

            return ResponseDto.success(responseDto,"리스트 제공에 성공하였습니다.");
        }else {
            // Storage별 조회
            Storage storage1 = Storage.valueOf(storage);
            List<MyIngredients> myIngredients = myIngredientsRepository.findByMemberIdAndStorage(member.getId(), storage1);
            List<MyIngredientResponseDto> dtoList = new ArrayList<>();
            StorageResponseDto responseDto = getStorageResponseDto(myIngredients, dtoList);

            return ResponseDto.success(responseDto,"리스트 제공에 성공하였습니다.");

        }


    }

    @Transactional(readOnly = true)
    public ResponseDto<?> getMyWarningIngredient(HttpServletRequest request) throws ParseException {
        //토큰 유효성 검사
        extracted(request);

        // 멤버 유효성 검사
        Member member = getMember();

        List<MyIngredients> myIngredients = myIngredientsRepository.findAllByMemberId(member.getId());
        List<MyIngredientResponseDto> outList = new ArrayList<>();
        List<MyIngredientResponseDto> hurryList = new ArrayList<>();

        //현재시각으로 d_day 구하기
        LocalDate now = LocalDate.now();
        String nowString = now.toString();

        for (MyIngredients myIngredient : myIngredients){
            Date outDay = new SimpleDateFormat("yyyy-MM-dd").parse(myIngredient.getExpDate());
            Date nowDay = new SimpleDateFormat("yyyy-MM-dd").parse(nowString);
            Long diffSec= (outDay.getTime()-nowDay.getTime())/1000;
            Long diffDays = diffSec / (24*60*60);
            String d_day;
            if(diffDays < 0){  // 유통기한 넘을시 추가..
                diffDays = -diffDays;
                d_day ="+"+diffDays.toString();
                outList.add(MyIngredientResponseDto.builder()
                        .id(myIngredient.getId())
                        .food_name(myIngredient.getIngredient().getFoodName())
                        .group_name(myIngredient.getIngredient().getFoodCategory())
                        .in_date(myIngredient.getInDate())
                        .d_date("D"+ d_day)
                        .build());

            }else if(diffDays < 5) {     // 7일 미만 HurryList 추가.
                d_day ="-"+diffDays.toString();
                hurryList.add(MyIngredientResponseDto.builder()
                        .id(myIngredient.getId())
                        .food_name(myIngredient.getIngredient().getFoodName())
                        .group_name(myIngredient.getIngredient().getFoodCategory())
                        .in_date(myIngredient.getInDate())
                        .d_date("D"+ d_day)
                        .build());
            }
        }

        WarningResponseDto responseDto = WarningResponseDto.builder()
                .out_dated_num(outList.size())
                .in_hurry_num(hurryList.size())
                .out_dated(outList)
                .in_hurry(hurryList)
                .build();

        return ResponseDto.success(responseDto,"리스트 제공에 성공하였습니다");
    }

    @Transactional
    public ResponseDto<?> deleteMyIngredient(Long myIngredientId, HttpServletRequest request) {
        //토큰 유효성 검사
        extracted(request);

        // 멤버 유효성 검사
        getMember();



        //재료 유효성 검사
        MyIngredients myIngredients = myIngredientsRepository.findById(myIngredientId).orElseThrow(
                () -> new IllegalArgumentException("이미 삭제된 재료입니다.")
        );

        //로그인한 멤버 id와 작성된 재료의 멤버 id와 다를시 예외처리
        if(!getMember().getId().equals(myIngredients.getMember().getId())){
            throw new RuntimeException("타인의 식재료를 삭제할 수 없습니다.");
        }

        myIngredientsRepository.delete(myIngredients);

        return ResponseDto.success("","재료 삭제가 성공하였습니다.");
    }

    private void extracted(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        token = resolveToken(token);
        tokenProvider.validateToken(token);
    }

    private Member getMember() {
        Member member = memberRepository.findById(SecurityUtil.getCurrentMemberId()).orElseThrow(
                () -> new IllegalArgumentException("해당 유저가 존재하지 않습니다.")
        );
        return member;
    }


    private String resolveToken(String token){
        if(token.startsWith("Bearer "))
            return token.substring(7);
        throw new RuntimeException("not valid token !!");
    }

    private StorageResponseDto getStorageResponseDto(List<MyIngredients> myIngredients, List<MyIngredientResponseDto> dtoList) throws ParseException {
        //현재시각으로 d_day 구하기
        LocalDate now = LocalDate.now();
        String nowString = now.toString();

        for (MyIngredients myIngredient : myIngredients) {
            Date outDay = new SimpleDateFormat("yyyy-MM-dd").parse(myIngredient.getExpDate());
            Date nowDay = new SimpleDateFormat("yyyy-MM-dd").parse(nowString);
            Long diffSec= (outDay.getTime()-nowDay.getTime())/1000;  //밀리초로 나와서 1000을 나눠야지 초 차이로됨
            Long diffDays = diffSec / (24*60*60); // 일자수 차이
            String d_day;
            if(diffDays < 0){
                diffDays = -diffDays;
                d_day ="+"+diffDays.toString();
            }else {
                d_day ="-"+diffDays.toString();
            }

            dtoList.add(MyIngredientResponseDto.builder()
                    .id(myIngredient.getId())
                    .food_name(myIngredient.getIngredient().getFoodName())
                    .group_name(myIngredient.getIngredient().getFoodCategory())
                    .in_date(myIngredient.getInDate())
                    .d_date("D"+ d_day)
                    .build());
        }

        StorageResponseDto responseDto = StorageResponseDto.builder()
                .storage(dtoList)
                .build();
        return responseDto;
    }

    // 나만의 냉장고 상태 표시
    @Transactional(readOnly = true)
    public RefrigeratorStateResponseDto MyRefrigeratorState() {
        Member member = memberRepository.findById(SecurityUtil.getCurrentMemberId()).orElseThrow(() -> {
            throw new UsernameNotFoundException("로그인 한 유저를 찾을 수 없습니다.");
        });
        List<MyIngredients> myIngredientsList = myIngredientsRepository.findAllByMemberId(member.getId());

        List<Integer> countList = new ArrayList<>();

        int warningCount = 0;
        int inHurryCount = 0;
        int fineCount = 0;

        // 5일 미만이면 hurry, < 0 warning
        for (MyIngredients myIngredients : myIngredientsList) {
            String match = "[^0-9]";
            int exp_date = Integer.parseInt(myIngredients.getExpDate().replaceAll(match,""));
            int in_date = Integer.parseInt(myIngredients.getInDate().replaceAll(match,""));
            if ((exp_date - in_date) < 5 && (exp_date - in_date) > 0) { // 남은 유통기한이 5일 미만일 때 = inHurry
                inHurryCount++;
            }  else if ((exp_date - in_date) >= 5) { // 남은 유통기한이 5일 이상일때
                fineCount++;
            } else if ((exp_date - in_date) < 0) { // 유통기한이 지난 재료
                warningCount++;
            }
        }

        int warningRate = warningCount/(inHurryCount+fineCount);

        countList.add(inHurryCount);
        countList.add(warningCount);
        countList.add(fineCount);

        String statusMsg = "";

        //유통기한지난거 1개라도있으면 관리 필요
        // warning/sumCount = 10: 냉장고가 건강해요! / 30: 냉장고가 슬슬 위험해요! / 50: 냉장고가 아파요!
        if (warningRate <= 0.1) {
            statusMsg = "냉장고가 건강해요!";
        } else if (warningRate > 0.1 && warningRate <= 0.45) {
            statusMsg = "냉장고가 슬슬 위험해요!";
        } else if (warningRate > 0.45) {
            statusMsg = "냉장고가 아파요";
        }

        RefrigeratorStateResponseDto refrigeratorStateResponseDto = RefrigeratorStateResponseDto.builder()
                .count(countList)
                .status_msg(statusMsg)
                .build();
        return refrigeratorStateResponseDto;
    }

    // 제품류 나눠서 보여주기
    @Transactional(readOnly = true)
    public IngredientsByCategoryResponseDto ingredientsByCategory() {
        Member member = memberRepository.findById(SecurityUtil.getCurrentMemberId()).orElseThrow(() -> {
            throw new UsernameNotFoundException("로그인 한 유저를 찾을 수 없습니다.");
        });
        // 농산물
        int produceNum = 0;
        // 축산물
        int livestockNum = 0;
        // 수산물
        int marineNum = 0;
        // 음료류
        int drinkNum = 0;
        // 기타
        int etcNum = 0;

        // 개수를 담을 list 생성
        List<Integer> countList = new ArrayList<>();
        List<MyIngredients> myIngredientsList = myIngredientsRepository.findAllByMemberId(member.getId());
        if (myIngredientsList.isEmpty()) {
            throw new IllegalArgumentException("해당 사용자가 입력한 식재료가 없습니다.");
        }
        // 카테고리별 재료 분류
        for (MyIngredients myIngredients : myIngredientsList) {
            switch (myIngredients.getIngredient().getFoodCategory()){
                // 농산물
                case 전분류: case 견과류: case 곡류: case 과실류: case 두류: case 버섯류: case 채소류:
                    produceNum++;
                    break;
                // 축산물
                case 난류: case 육류:
                    livestockNum++;
                    break;
                // 수산물
                case 어패류: case 해조류:
                    marineNum++;
                    break;
                // 음료류
                case 음료류: case 주류: case 차류:
                    drinkNum++;
                    break;
                // 기타
                case 기타: case 당류: case 유제품류: case 조리가공품류: case 유지류: case 조미료류:
                    etcNum++;
                    break;
            }
        }
        countList.add(produceNum);
        countList.add(livestockNum);
        countList.add(marineNum);
        countList.add(drinkNum);
        countList.add(etcNum);

        List<String> statusMsgList = new ArrayList<>();
        statusMsgList.add("오늘은 농산물 축제 어때요?");
        statusMsgList.add("오늘은 고기 어때요?");
        statusMsgList.add("오늘은 해산물 요리 어때요?");

        List<String> randomMsg = new ArrayList<>();
        String statusMsg = "";

        int max = countList.get(0);
        // 개수가 같으면 처음최대값이 출력됨
        for (int i = 0; i < countList.size()-2; i++) {
            if (countList.get(i) > max) {
                max = countList.get(i);
            }
        }

        // 수정 필요함
        for (int i = 0; i < countList.size() - 2; i++) {
            // 모든 값이 같을 때 모든 메세지 랜덤으로 출력
            if (produceNum == livestockNum && produceNum == marineNum) {
                Double random = Math.random();
                int num = (int) Math.round(random * (statusMsgList.size() - 1));
                statusMsg = statusMsgList.get(num);
                break;
                // 농산물과 축산물이 같고 최대 값일때 메세지 두가지만 출력
            } else if (produceNum == livestockNum && produceNum == max) {
                randomMsg.add(statusMsgList.get(0));
                randomMsg.add(statusMsgList.get(1));
                Double random = Math.random();
                int num = (int) Math.round(random * (randomMsg.size() - 1));
                statusMsg = randomMsg.get(num);
                // 축산물과 수산물이 같고 축산물이 최대값일때
            } else if (livestockNum == marineNum && livestockNum ==max) {
                randomMsg.add(statusMsgList.get(1));
                randomMsg.add(statusMsgList.get(2));
                Double random = Math.random();
                int num = (int) Math.round(random * (randomMsg.size() - 1));
                statusMsg = randomMsg.get(num);
                // 수산물과 농산물이 같고 수산물이 최대값일때
            } else if (marineNum == produceNum && marineNum ==max) {
                randomMsg.add(statusMsgList.get(2));
                randomMsg.add(statusMsgList.get(0));
                Double random = Math.random();
                int num = (int) Math.round(random * (randomMsg.size() - 1));
                statusMsg = randomMsg.get(num);
            } else if (countList.get(i).equals(countList.get(0)) && countList.get(i) == max) {
                randomMsg.add(statusMsgList.get(0));
                randomMsg.add(statusMsgList.get(i));
                Double random = Math.random();
                int num = (int) Math.round(random * (randomMsg.size() - 1));
                statusMsg = randomMsg.get(num);
            } else if (countList.get(i) > countList.get(0) && countList.get(i) == max) {
                statusMsg = statusMsgList.get(i);
                break;

            }
        }


        IngredientsByCategoryResponseDto ingredientsByCategoryResponseDto = IngredientsByCategoryResponseDto.builder()
                .count(countList)
                .status_msg(statusMsg)
                .build();
        return ingredientsByCategoryResponseDto;
    }

    @Transactional(readOnly = true)
    public ResponseDto<?> getAllMyIngredient(HttpServletRequest request) throws ParseException {
        //토큰 유효성 검사
        extracted(request);

        // 멤버 유효성 검사
        Member member = getMember();

        List<MyIngredients> myIngredients = myIngredientsRepository.findAllByMemberId(member.getId());
        List<TotalMyIngredientDto> dtoList = new ArrayList<>();

        //현재시각으로 d_day 구하기
        LocalDate now = LocalDate.now();
        String nowString = now.toString();


        for (MyIngredients myIngredient : myIngredients) {
            Date outDay = new SimpleDateFormat("yyyy-MM-dd").parse(myIngredient.getExpDate());
            Date nowDay = new SimpleDateFormat("yyyy-MM-dd").parse(nowString);
            Long diffSec= (outDay.getTime()-nowDay.getTime())/1000;  //밀리초로 나와서 1000을 나눠야지 초 차이로됨
            Long diffDays = diffSec / (24*60*60); // 일자수 차이
            String d_day;
            if(diffDays < 0){
                diffDays = -diffDays;
                d_day ="+"+diffDays.toString();
            }else {
                d_day ="-"+diffDays.toString();
            }

            dtoList.add(TotalMyIngredientDto.builder()
                    .id(myIngredient.getId())
                    .food_name(myIngredient.getIngredient().getFoodName())
                    .group_name(myIngredient.getIngredient().getFoodCategory())
                    .in_date(myIngredient.getInDate())
                    .d_date("D"+ d_day)
                    .category(myIngredient.getStorage())
                    .build());
        }

        ListTotalMyIngredientsDto responseDto = ListTotalMyIngredientsDto.builder()
                .ingredients_num(dtoList.size())
                .storage(dtoList)
                .build();

        return ResponseDto.success(responseDto,"리스트 제공에 성공하였습니다.");
    }
}
