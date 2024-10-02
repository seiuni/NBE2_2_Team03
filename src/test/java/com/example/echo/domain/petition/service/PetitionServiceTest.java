package com.example.echo.domain.petition.service;

import com.example.echo.domain.member.entity.Member;
import com.example.echo.domain.member.entity.Role;
import com.example.echo.domain.member.repository.MemberRepository;
import com.example.echo.domain.petition.dto.request.PagingRequestDto;
import com.example.echo.domain.petition.dto.request.PetitionRequestDto;
import com.example.echo.domain.petition.dto.request.SortBy;
import com.example.echo.domain.petition.dto.response.PetitionResponseDto;
import com.example.echo.domain.petition.entity.Category;
import com.example.echo.domain.petition.entity.Petition;
import com.example.echo.domain.petition.repository.PetitionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PetitionServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PetitionService petitionService;

    @Autowired
    private PetitionRepository petitionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member testMember;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        petitionRepository.deleteAll();
        memberRepository.deleteAll();
        testMember = createMember();
        accessToken = loginAndGetToken(Role.ADMIN);
    }

    private String loginAndGetToken(Role role) throws Exception {
        String userId = role == Role.USER ? "testUser" : "testAdmin";
        String requestJson = "{"
                + "\"userId\": \"" + userId + "\","
                + "\"password\": \"password123\""
                + "}";

        String response = mockMvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(response).read("data.accessToken");
    }

    @Test
    @DisplayName("청원 생성")
    void createPetition() {
        // given
        PetitionRequestDto request = createPetitionRequest(testMember.getMemberId());

        // when
        PetitionResponseDto response = petitionService.createPetition(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("테스트 청원");
        assertThat(petitionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("청원 단건 조회")
    void getPetitionById() {
        // given
        Petition petition = createPetition(testMember);

        // when
        PetitionResponseDto response = petitionService.getPetitionById(petition.getPetitionId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("테스트 청원");
    }

    @Test
    @DisplayName("청원 전체 조회 - 좋아요 순 정렬")
    void getPetitions_SortByLikes() {
        // given
        createMultiplePetitions(5);
        PagingRequestDto pagingRequestDto = new PagingRequestDto();
        pagingRequestDto.setSortBy(SortBy.LIKES_COUNT);
        pagingRequestDto.setDirection("desc");

        // when
        Page<PetitionResponseDto> petitionsPage = petitionService.getPetitions(pagingRequestDto.toPageable(), pagingRequestDto.getCategory());

        // then
        List<PetitionResponseDto> petitions = petitionsPage.getContent();
        assertThat(petitions).hasSize(5);
        // p2의 좋아요 수가 p1보다 많으면 양수 반환, 이는 정렬 알고리즘에 의해 p2가 앞에 오도록 처리
        assertThat(petitions).isSortedAccordingTo((p1, p2) -> p2.getLikesCount().compareTo(p1.getLikesCount()));
    }

    @Test
    @DisplayName("청원 전체 조회 - 만료일 순 정렬")
    void getPetitions_SortByExpirationDate() {
        // given
        createMultiplePetitions(5);
        PagingRequestDto pagingRequestDto = new PagingRequestDto();
        pagingRequestDto.setSortBy(SortBy.END_DATE);
        pagingRequestDto.setDirection("desc");

        // when
        Page<PetitionResponseDto> petitionsPage = petitionService.getPetitions(pagingRequestDto.toPageable(), pagingRequestDto.getCategory());

        // then
        List<PetitionResponseDto> petitions = petitionsPage.getContent();
        assertThat(petitions).hasSize(5);
        assertThat(petitions).isSortedAccordingTo((p1, p2) -> p2.getEndDate().compareTo(p1.getEndDate()));
    }

    @Test
    @DisplayName("청원 전체 조회 - 카테고리별 정렬")
    void getPetitions_FilterByCategory() {
        // given
        createMultiplePetitions(10);
        PagingRequestDto pagingRequestDto = new PagingRequestDto();
        pagingRequestDto.setCategory(Category.EDUCATION);

        // when
        Page<PetitionResponseDto> petitionsPage = petitionService.getPetitions(pagingRequestDto.toPageable(), pagingRequestDto.getCategory());

        // then
        List<PetitionResponseDto> petitions = petitionsPage.getContent();
        // petitions 컬렉션의 모든 요소(petition)의 카테고리가 EDUCATION 인지 확인
        assertThat(petitions).allMatch(petition -> petition.getCategory() == Category.EDUCATION);
    }

    @Test
    @DisplayName("청원 수정")
    void updatePetition() {
        // given
        Petition petition = createPetition(testMember); // 청원 생성
        LocalDateTime newStartDate = LocalDateTime.now().plusDays(1);
        LocalDateTime newEndDate = LocalDateTime.now().plusDays(31);

        PetitionRequestDto updateRequest = PetitionRequestDto.builder() // 청원 수정 요청
                .memberId(testMember.getMemberId())
                .title("수정된 청원 제목")
                .content("수정된 청원 내용")
                .summary("수정된 청원 요약")
                .startDate(newStartDate)
                .endDate(newEndDate)
                .category(Category.EDUCATION)
                .originalUrl("http://updated-test.com")
                .relatedNews("수정된 관련 뉴스")
                .build();

        // when
        PetitionResponseDto updatedPetition = petitionService.updatePetition(petition.getPetitionId(), updateRequest);

        // then
        assertThat(updatedPetition.getTitle()).isEqualTo("수정된 청원 제목");
        assertThat(updatedPetition.getContent()).isEqualTo("수정된 청원 내용");
        assertThat(updatedPetition.getSummary()).isEqualTo("수정된 청원 요약");
        assertThat(updatedPetition.getStartDate()).isEqualToIgnoringNanos(newStartDate);
        assertThat(updatedPetition.getEndDate()).isEqualToIgnoringNanos(newEndDate);
        assertThat(updatedPetition.getCategory()).isEqualTo(Category.EDUCATION);
        assertThat(updatedPetition.getOriginalUrl()).isEqualTo("http://updated-test.com");
        assertThat(updatedPetition.getRelatedNews()).isEqualTo("수정된 관련 뉴스");
    }

    @Test
    @DisplayName("청원 삭제")
    void deletePetition() {
        // given
        Petition petition = createPetition(testMember);

        // when
        petitionService.deletePetitionById(petition.getPetitionId());

        // then
        assertThat(petitionRepository.count()).isEqualTo(0);
    }

    /**
     * [테스트용 관리자 회원 생성]
     * 이 메소드는 청원 관련 테스트에 필요한 관리자 권한을 가진 회원을 생성
     */
    private Member createMember() {
        Member member = Member.builder()
                .userId("testAdmin")
                .name("김관리")
                .email("test@example.com")
                .password(passwordEncoder.encode("password123"))
                .phone("010-1234-5678")
                .avatarImage("default.jpg")
                .role(Role.ADMIN)
                .build();
        return memberRepository.save(member);
    }

    /**
     * [청원 등록 요청을 위한 DTO 생성]
     * 청원 등록 시 관리자가 입력하는 요청 데이터를 모방하여 PetitionRequestDto 생성
     */
    private PetitionRequestDto createPetitionRequest(Long memberId) {
        return PetitionRequestDto.builder()
                .memberId(memberId)
                .title("테스트 청원")
                .content("테스트 내용")
                .summary("테스트 요약")
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .category(Category.POLITICS)
                .originalUrl("http://test.com")
                .relatedNews("테스트 뉴스")
                .build();
    }

    /**
     * [테스트용 청원 엔티티 생성]
     * 이 메소드는 청원 조회/수정/삭제 테스트에 필요한 청원 데이터를 데이터베이스에 저장
     */
    private Petition createPetition(Member member) {
        Petition petition = Petition.builder()
                .member(member)
                .title("테스트 청원")
                .content("테스트 청원 내용")
                .summary("테스트 청원 요약")
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .category(Category.POLITICS)
                .originalUrl("http://test.com")
                .relatedNews("테스트 관련 뉴스")
                .agreeCount((int) (Math.random() * 1000))
                .build();
        return petitionRepository.save(petition);
    }

    /**
     * [페이징 테스트용 다수 청원 엔티티 생성]
     * 정렬 테스트를 위해 각각 다른 값 부여
     */
    private void createMultiplePetitions(int count) {
        for (int i = 0; i < count; i++) {
            Petition petition = Petition.builder()
                    .member(testMember)
                    .title("테스트 청원 " + i)
                    .content("테스트 청원 내용 " + i)
                    .summary("테스트 청원 요약 " + i)
                    .startDate(LocalDateTime.now().plusDays(i))
                    .endDate(LocalDateTime.now().plusDays(30 + i))
                    .category(i % 2 == 0 ? Category.POLITICS : Category.EDUCATION)
                    .originalUrl("http://test" + i + ".com")
                    .relatedNews("테스트 관련 뉴스 " + i)
                    .agreeCount((int) (Math.random() * 1000))
                    .build();
            petitionRepository.save(petition);
        }
    }
}
