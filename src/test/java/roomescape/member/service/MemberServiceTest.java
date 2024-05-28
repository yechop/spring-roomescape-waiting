package roomescape.member.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import roomescape.exceptions.NotFoundException;
import roomescape.login.dto.LoginRequest;
import roomescape.member.domain.Member;

import javax.naming.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static roomescape.InitialMemberFixture.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(scripts = {"/schema.sql", "/initial_test_data.sql"})
class MemberServiceTest {

    @Autowired
    private MemberService memberService;

    @Test
    @DisplayName("존재하지 않는 회원 email로 로그인을 시도할 경우 예외가 발생한다.")
    void throwExceptionIfNotExistEmail() {
        LoginRequest loginRequest = new LoginRequest(
                COMMON_PASSWORD.password(),
                NOT_SAVED_MEMBER.getEmail().email()
        );

        assertThatThrownBy(() -> memberService.createMemberToken(loginRequest))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("존재하는 회원 email이지만 틀린 비밀번호로 로그인을 시도할 경우 예외가 발생한다.")
    void throwExceptionIfInvalidPassword() {
        LoginRequest loginRequest = new LoginRequest(
                COMMON_PASSWORD.password() + "123",
                MEMBER_1.getEmail().email()
        );

        assertThatThrownBy(() -> memberService.createMemberToken(loginRequest))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("로그인에 성공하면 토큰을 발행한다.")
    void getTokenIfLoginSucceeds() throws AuthenticationException {
        LoginRequest loginRequest = new LoginRequest(COMMON_PASSWORD.password(), MEMBER_4.getEmail().email());

        String token = memberService.createMemberToken(loginRequest);

        assertThat(token).isNotNull();
    }

    @Test
    @DisplayName("유효하지 않은 형식의 토큰으로 로그인 시도 시 예외가 발생한다.")
    void throwExceptionIfInvalidTokenFormat() {
        assertThatThrownBy(() -> memberService.getLoginMemberByToken("invalid token"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("토큰에 대응하는 멤버 정보를 가져온다.")
    void getMemberMember() throws AuthenticationException {
        LoginRequest loginRequest = new LoginRequest(COMMON_PASSWORD.password(), MEMBER_4.getEmail().email());
        String token = memberService.createMemberToken(loginRequest);

        Member member = memberService.getLoginMemberByToken(token);

        assertThat(member.getName().name()).isEqualTo(MEMBER_4.getName().name());
    }
}
