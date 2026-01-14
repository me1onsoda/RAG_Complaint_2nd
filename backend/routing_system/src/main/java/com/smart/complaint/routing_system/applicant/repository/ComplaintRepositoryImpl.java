package com.smart.complaint.routing_system.applicant.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.smart.complaint.routing_system.applicant.domain.ComplaintStatus;
import com.smart.complaint.routing_system.applicant.domain.UrgencyLevel;
import com.smart.complaint.routing_system.applicant.dto.ComplaintDetailResponse;
import com.smart.complaint.routing_system.applicant.dto.ComplaintResponse;
import com.smart.complaint.routing_system.applicant.dto.ComplaintSearchCondition;
import com.smart.complaint.routing_system.applicant.dto.ComplaintSearchResult;
import com.smart.complaint.routing_system.applicant.entity.Complaint;
import com.smart.complaint.routing_system.applicant.dto.ComplaintDetailDto;
import com.smart.complaint.routing_system.applicant.dto.ComplaintDto;
import com.smart.complaint.routing_system.applicant.dto.ComplaintResponse;
import com.smart.complaint.routing_system.applicant.dto.ComplaintSearchCondition;
import com.smart.complaint.routing_system.applicant.dto.ComplaintSearchResult;
import com.smart.complaint.routing_system.applicant.entity.QComplaint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Collectors;
import static com.smart.complaint.routing_system.applicant.entity.QComplaint.complaint;
import com.smart.complaint.routing_system.applicant.entity.QComplaintNormalization;
import com.smart.complaint.routing_system.applicant.entity.*;
import com.smart.complaint.routing_system.applicant.entity.QComplaintNormalization; // 팀원의 Q클래스
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class ComplaintRepositoryImpl implements ComplaintRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QComplaintNormalization normalization = QComplaintNormalization.complaintNormalization;

    @Override
    public List<ComplaintResponse> search(Long departmentId, ComplaintSearchCondition condition) {

        // Tuple로 조회 (민원 + 요약문)
        List<Tuple> results = queryFactory
                .select(complaint, normalization.neutralSummary)
                .from(complaint)
                // 요약 정보를 가져오기 위해 조인 (없을 수도 있으니 Left Join)
                .leftJoin(normalization).on(normalization.complaint.eq(complaint))
                .where(
                        complaint.currentDepartmentId.eq(departmentId),
                        keywordContains(condition.getKeyword()),
                        statusEq(condition.getStatus()),
                        hasIncident(condition.getHasIncident()))
                .orderBy(getOrderSpecifier(condition.getSort())) // 정렬 적용
                .fetch();

        // Tuple -> DTO 매핑
        return results.stream()
                .map(tuple -> {
                    Complaint c = tuple.get(complaint);
                    String summary = tuple.get(normalization.neutralSummary);

                    ComplaintResponse dto = new ComplaintResponse(c);
                    dto.setNeutralSummary(summary); // 요약문 주입
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ComplaintSearchResult> findSimilarComplaint(double[] queryEmbedding, int limit) {
        String vectorString = java.util.Arrays.toString(queryEmbedding);

        // PGVector 코사인 거리 계산 (1 - Cosine Distance)
        NumberTemplate<Double> similarity = Expressions.numberTemplate(Double.class,
                "1 - ({0} <-> cast({1} as vector))",
                normalization.embedding,
                vectorString);

        return queryFactory
                .select(Projections.constructor(ComplaintSearchResult.class,
                        complaint.id,
                        complaint.title,
                        complaint.body,
                        similarity.as("score")))
                .from(normalization)
                .join(normalization.complaint, complaint)
                .where(normalization.isCurrent.isTrue())
                .orderBy(similarity.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<ComplaintDto> findTop3RecentComplaintByApplicantId(Long applicantId) {
        QComplaint complaint = QComplaint.complaint;

        return queryFactory
                .select(Projections.constructor(ComplaintDto.class,
                        complaint.id,
                        complaint.title,
                        complaint.status, // 엔티티의 Enum 타입
                        complaint.createdAt // 엔티티의 LocalDateTime 타입
                ))
                .from(complaint)
                .where(complaint.applicantId.eq(applicantId))
                .orderBy(complaint.createdAt.desc())
                .limit(3)
                .fetch();
    }

    @Override
    public List<ComplaintDetailDto> findAllByApplicantId(Long applicantId, String keyword) {
        QComplaint complaint = QComplaint.complaint;

        return queryFactory
                .select(Projections.constructor(ComplaintDetailDto.class,
                        complaint.id,
                        complaint.title,
                        complaint.body,
                        complaint.answer,
                        complaint.addressText,
                        complaint.status,
                        complaint.createdAt,
                        complaint.updatedAt
                // record의 생성자 파라미터 순서와 데이터 타입이 정확이 일치해야함
                ))
                .from(complaint)
                .where(
                        complaint.applicantId.eq(applicantId),
                        titleContains(keyword))
                .orderBy(complaint.createdAt.desc())
                .fetch();
    }

    // --- 조건 메서드 ---
    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isEmpty())
            return null;
        return complaint.title.contains(keyword)
                .or(complaint.body.contains(keyword));
    }

    private BooleanExpression statusEq(ComplaintStatus status) {
        return status != null ? complaint.status.eq(status) : null;
    }

    private BooleanExpression hasIncident(Boolean hasIncident) {
        if (hasIncident == null) return null;
        return hasIncident ? complaint.incident.isNotNull() : complaint.incident.isNull();
        // Complaint 엔티티의 Long incidentId -> Incident incident 로 교체하면서
        // ㅑncidentId 대신 incident 객체 자체의 존재 여부 확인
    }

    private BooleanExpression titleContains(String keyword) {
        // 검색어가 없으면(null 또는 빈 문자열) null을 반환 -> where 절에서 무시됨
        return StringUtils.hasText(keyword) ? QComplaint.complaint.title.contains(keyword) : null;
    }

    // --- 정렬 메서드 (Sort) ---
    private OrderSpecifier<?> getOrderSpecifier(String sort) {
        if ("status".equals(sort)) {
            return complaint.status.asc();
        }
        // 기본값: 최신순
        return complaint.receivedAt.desc();
    }

    @Override
    public ComplaintDetailResponse getComplaintDetail(Long complaintId) {
        QComplaint complaint = QComplaint.complaint;
        QComplaintNormalization normalization = QComplaintNormalization.complaintNormalization;
        QIncident incident = QIncident.incident;
        QDepartment department = QDepartment.department;

        // 1. 사건에 묶인 민원 수 계산 (SubQuery)
        var incidentCountSubQuery = JPAExpressions
                .select(complaint.count())
                .from(complaint)
                .where(complaint.incident.id.eq(incident.id));

        // 2. 조인 쿼리 실행
        Tuple result = queryFactory
                .select(
                        complaint,
                        normalization,
                        incident,
                        department.name, // 부서명 (없으면 null)
                        incidentCountSubQuery
                )
                .from(complaint)
                // 정규화 정보 조인 (Left Join: 분석 안 된 민원도 보여야 함)
                .leftJoin(normalization).on(normalization.complaint.eq(complaint))
                // 사건 정보 조인 (Left Join: 사건 없는 민원도 보여야 함)
                .leftJoin(complaint.incident, incident)
                // 부서 정보 조인 (Left Join)
                .leftJoin(department).on(complaint.currentDepartmentId.eq(department.id))
                .where(complaint.id.eq(complaintId))
                .fetchOne();

        if (result == null) {
            return null;
        }

        // 3. 결과 추출 및 DTO 변환
        Complaint c = result.get(complaint);
        ComplaintNormalization n = result.get(normalization);
        Incident i = result.get(incident);
        String deptName = result.get(department.name);
        Long iCount = result.get(incidentCountSubQuery);
        if (iCount == null) iCount = 0L;

        return new ComplaintDetailResponse(c, n, i, iCount, deptName);
    }
}