package io.fundrequest.core.request.claim;

import io.fundrequest.common.infrastructure.mapping.Mappers;
import io.fundrequest.core.PrincipalMother;
import io.fundrequest.core.request.claim.domain.Claim;
import io.fundrequest.core.request.claim.domain.ClaimRequestStatus;
import io.fundrequest.core.request.claim.domain.RequestClaim;
import io.fundrequest.core.request.claim.dto.ClaimDto;
import io.fundrequest.core.request.claim.dto.ClaimsByTransactionAggregate;
import io.fundrequest.core.request.claim.event.RequestClaimedEvent;
import io.fundrequest.core.request.claim.github.GithubClaimResolver;
import io.fundrequest.core.request.claim.infrastructure.ClaimRepository;
import io.fundrequest.core.request.claim.infrastructure.RequestClaimRepository;
import io.fundrequest.core.request.domain.Request;
import io.fundrequest.core.request.domain.RequestMother;
import io.fundrequest.core.request.domain.RequestStatus;
import io.fundrequest.core.request.infrastructure.RequestRepository;
import io.fundrequest.core.request.view.ClaimDtoMother;
import io.fundrequest.core.request.view.RequestDto;
import io.fundrequest.core.request.view.RequestDtoMother;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.fundrequest.core.request.claim.domain.ClaimRequestStatus.PROCESSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClaimServiceImplTest {

    private ClaimServiceImpl claimService;

    private RequestRepository requestRepository;
    private RequestClaimRepository requestClaimRepository;
    private ClaimRepository claimRepository;
    private GithubClaimResolver githubClaimResolver;
    private Mappers mappers;
    private ClaimDtoAggregator claimDtoAggregator;
    private ApplicationEventPublisher applicationEventPublisher;

    @Before
    public void setUp() {
        requestRepository = mock(RequestRepository.class);
        claimRepository = mock(ClaimRepository.class);
        requestClaimRepository = mock(RequestClaimRepository.class);
        githubClaimResolver = mock(GithubClaimResolver.class);
        mappers = mock(Mappers.class);
        claimDtoAggregator = mock(ClaimDtoAggregator.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        claimService = new ClaimServiceImpl(requestRepository,
                                            claimRepository,
                                            requestClaimRepository,
                                            githubClaimResolver,
                                            mappers,
                                            claimDtoAggregator,
                                            applicationEventPublisher);
    }

    @Test
    public void findClaim() {
        final long claimId = 697L;
        final Claim claim = Claim.builder().build();
        final ClaimDto expected = ClaimDtoMother.aClaimDto().build();

        when(claimRepository.findOne(claimId)).thenReturn(Optional.of(claim));
        when(mappers.map(eq(Claim.class), eq(ClaimDto.class), same(claim))).thenReturn(expected);

        final Optional<ClaimDto> result = claimService.findOne(claimId);

        assertThat(result).containsSame(expected);
    }

    @Test
    public void findClaim_notFound() {
        final long claimId = 697L;

        when(claimRepository.findOne(claimId)).thenReturn(Optional.empty());

        final Optional<ClaimDto> result = claimService.findOne(claimId);

        assertThat(result).isEmpty();
    }

    @Test
    public void claim() {
        Principal solver = PrincipalMother.davyvanroy();
        UserClaimRequest userClaimRequest = UserClaimRequestMother.kazuki43zooApiStub().build();
        Request request = RequestMother.freeCodeCampNoUserStories().build();
        when(requestRepository.findByPlatformAndPlatformId(userClaimRequest.getPlatform(), userClaimRequest.getPlatformId()))
                .thenReturn(Optional.of(request));
        RequestDto requestDto = RequestDtoMother.freeCodeCampNoUserStories();
        when(mappers.map(Request.class, RequestDto.class, request)).thenReturn(requestDto);
        when(githubClaimResolver.getUserPlatformUsername(solver, request.getIssueInformation().getPlatform()))
                .thenReturn(Optional.of(solver.getName()));
        when(githubClaimResolver.canClaim(solver, requestDto)).thenReturn(true);

        claimService.claim(solver, userClaimRequest);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CLAIM_REQUESTED);
        verify(requestRepository).save(request);

        ArgumentCaptor<RequestClaim> requestClaimArgumentCaptor = ArgumentCaptor.forClass(RequestClaim.class);
        verify(requestClaimRepository).save(requestClaimArgumentCaptor.capture());
        RequestClaim requestClaim = requestClaimArgumentCaptor.getValue();
        assertThat(requestClaim.getRequestId()).isEqualTo(request.getId());
        assertThat(requestClaim.getAddress()).isEqualTo(userClaimRequest.getAddress());
        assertThat(requestClaim.getSolver()).isEqualTo(solver.getName());
        assertThat(requestClaim.getFlagged()).isFalse();
        assertThat(requestClaim.getStatus()).isEqualTo(ClaimRequestStatus.PENDING);
    }

    @Test
    public void getClaimedBy() {
        final long requestId = 567L;
        final List<Claim> claims = new ArrayList<>();
        final List<ClaimDto> claimDtos = new ArrayList<>();
        final Principal principal = mock(Principal.class);
        final ClaimsByTransactionAggregate claimsByTransactionAggregate = mock(ClaimsByTransactionAggregate.class);

        when(principal.getName()).thenReturn("hfgj");
        when(claimRepository.findByRequestId(requestId)).thenReturn(claims);
        when(mappers.mapList(eq(Claim.class), eq(ClaimDto.class), same(claims))).thenReturn(claimDtos);
        when(claimDtoAggregator.aggregateClaims(same(claimDtos))).thenReturn(claimsByTransactionAggregate);

        final ClaimsByTransactionAggregate result = claimService.getAggregatedClaimsForRequest(requestId);

        assertThat(result).isSameAs(claimsByTransactionAggregate);
    }

    @Test
    public void onClaimed() {
        long requestId = 3124L;
        final RequestDto requestDto = RequestDtoMother.fundRequestArea51();
        requestDto.setId(requestId);
        final RequestClaim requestClaim1 = RequestClaim.builder().status(ClaimRequestStatus.PENDING).build();
        final RequestClaim requestClaim2 = RequestClaim.builder().status(ClaimRequestStatus.PENDING).build();

        when(requestClaimRepository.findByRequestId(requestId)).thenReturn(Arrays.asList(requestClaim1, requestClaim2));

        claimService.onClaimed(RequestClaimedEvent.builder()
                                                  .blockchainEventId(324L)
                                                  .requestDto(requestDto)
                                                  .build());

        assertThat(requestClaim1.getStatus()).isEqualTo(PROCESSED);
        assertThat(requestClaim2.getStatus()).isEqualTo(PROCESSED);
        verify(requestClaimRepository).save(requestClaim1);
        verify(requestClaimRepository).save(requestClaim2);
    }
}
