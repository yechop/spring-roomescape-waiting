package roomescape.reservation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.admin.dto.AdminReservationRequest;
import roomescape.exceptions.DuplicationException;
import roomescape.exceptions.NotFoundException;
import roomescape.exceptions.ValidationException;
import roomescape.member.domain.Member;
import roomescape.member.dto.MemberRequest;
import roomescape.member.service.MemberService;
import roomescape.reservation.domain.Reservation;
import roomescape.reservation.domain.Waiting;
import roomescape.reservation.dto.ReservationRequest;
import roomescape.reservation.dto.ReservationResponse;
import roomescape.reservation.dto.ReservationTimeResponse;
import roomescape.reservation.repository.ReservationJpaRepository;
import roomescape.theme.domain.Theme;
import roomescape.theme.dto.ThemeResponse;
import roomescape.theme.service.ThemeService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ReservationService {

    private final ReservationJpaRepository reservationJpaRepository;
    private final ReservationTimeService reservationTimeService;
    private final ThemeService themeService;
    private final MemberService memberService;
    private final WaitingService waitingService;

    public ReservationService(
            ReservationJpaRepository ReservationJpaRepository,
            ReservationTimeService reservationTimeService,
            ThemeService themeService,
            MemberService memberService,
            WaitingService waitingService
    ) {
        this.reservationJpaRepository = ReservationJpaRepository;
        this.reservationTimeService = reservationTimeService;
        this.themeService = themeService;
        this.memberService = memberService;
        this.waitingService = waitingService;
    }

    @Transactional
    public ReservationResponse addReservation(
            ReservationRequest reservationRequest,
            MemberRequest memberRequest
    ) {
        ReservationTimeResponse timeResponse = reservationTimeService.getTime(reservationRequest.timeId());
        ThemeResponse themeResponse = themeService.getTheme(reservationRequest.themeId());

        Reservation reservation = new Reservation(
                reservationRequest.date(),
                timeResponse.toReservationTime(),
                themeResponse.toTheme(),
                memberRequest.toLoginMember()
        );
        validateIsBeforeNow(reservation);
        validateIsDuplicated(reservation);

        return new ReservationResponse(reservationJpaRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse addReservation(AdminReservationRequest adminReservationRequest) {
        Member member = memberService.getLoginMemberById(adminReservationRequest.memberId());
        ReservationTimeResponse timeResponse = reservationTimeService.getTime(adminReservationRequest.timeId());
        ThemeResponse themeResponse = themeService.getTheme(adminReservationRequest.themeId());

        Reservation reservation = new Reservation(
                adminReservationRequest.date(),
                timeResponse.toReservationTime(),
                themeResponse.toTheme(),
                member
        );
        validateIsBeforeNow(reservation);
        validateIsDuplicated(reservation);

        return new ReservationResponse(reservationJpaRepository.save(reservation));
    }

    private void validateIsBeforeNow(Reservation reservation) {
        if (reservation.isBeforeNow()) {
            throw new ValidationException("과거 시간은 예약할 수 없습니다.");
        }
    }

    private void validateIsDuplicated(Reservation reservation) {
        if (reservationJpaRepository.existsByDateAndReservationTimeAndTheme(
                reservation.getDate(),
                reservation.getReservationTime(),
                reservation.getTheme())
        ) {
            throw new DuplicationException("이미 예약이 존재합니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findReservations() {
        return reservationJpaRepository.findAll()
                .stream()
                .map(ReservationResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> searchReservations(
            Long themeId,
            Long memberId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        Theme theme = themeService.getById(themeId);
        Member member = memberService.getById(memberId);
        return reservationJpaRepository.findByThemeAndMember(theme, member)
                .stream()
                .filter(reservation -> reservation.isBetweenInclusive(dateFrom, dateTo))
                .map(ReservationResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findReservationsByMember(MemberRequest memberRequest) {
        Stream<ReservationResponse> reservations =
                reservationJpaRepository.findByMember(memberRequest.toLoginMember())
                .stream()
                .map(ReservationResponse::new);

        Stream<ReservationResponse> waitings =
                waitingService.findWaitingsByMember(memberRequest.toLoginMember())
                .stream()
                .map(ReservationResponse::fromWaitingWithRank);

        return Stream.concat(reservations, waitings).
                sorted(Comparator.comparing(ReservationResponse::date)
                        .thenComparing(reservationResponse ->
                                reservationResponse.time().toReservationTime().getStartAt()
                        )
                )
                .toList();
    }

    @Transactional
    public void deleteReservation(Long id, Member member) {
        Reservation reservation = reservationJpaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("id와 일치하는 예약을 찾을 수 없습니다."));

        if (reservation.getMember().equals(member)) {
            waitingService.findWaitingByDateAndReservationTimeAndTheme(
                        reservation.getDate(),
                        reservation.getReservationTime(),
                        reservation.getTheme()
                    )
                    .ifPresentOrElse(
                            waiting -> updateReservationByWaiting(waiting, reservation, member),
                            () -> reservationJpaRepository.deleteById(id)
                    );
        }
    }

    private void updateReservationByWaiting(Waiting waiting, Reservation reservation, Member member) {
        Reservation changedReservation = new Reservation(reservation.getId(),
                reservation.getDate(),
                reservation.getReservationTime(),
                reservation.getTheme(),
                waiting.getMember()
                );
        reservationJpaRepository.save(changedReservation);
        waitingService.deleteById(waiting.getId(), member);
    }
}
