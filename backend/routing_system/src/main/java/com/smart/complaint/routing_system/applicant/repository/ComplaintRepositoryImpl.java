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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Collectors;
import static com.smart.complaint.routing_system.applicant.entity.QComplaint.complaint;
import com.smart.complaint.routing_system.applicant.entity.QComplaintNormalization;
import com.smart.complaint.routing_system.applicant.entity.*;

@Repository
@RequiredArgsConstructor
public class ComplaintRepositoryImpl implements ComplaintRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QComplaintNormalization normalization = QComplaintNormalization.complaintNormalization;
    private final QUser user = QUser.user;

    @Override
    public List<ComplaintResponse> search(Long departmentId, ComplaintSearchCondition condition) {

        // Tuple로 조회 (민원 + 요약문)
        List<Tuple> results = queryFactory
                .select(complaint, normalization.neutralSummary, user.displayName)
                .from(complaint)
                // 요약 정보를 가져오기 위해 조인 (없을 수도 있으니 Left Join)
                .leftJoin(normalization).on(normalization.complaint.eq(complaint))
                .leftJoin(user).on(complaint.answeredBy.eq(user.id))
                .where(
                        complaint.currentDepartmentId.eq(departmentId),
                        keywordContains(condition.getKeyword()),
                        statusEq(condition.getStatus()),
                        urgencyEq(condition.getUrgency()),
                        hasIncident(condition.getHasIncident())
                )
                .orderBy(getOrderSpecifier(condition.getSort()))
                .fetch();

        // Tuple -> DTO 매핑
        return results.stream()
                .map(tuple -> {
                    Complaint c = tuple.get(complaint);
                    String summary = tuple.get(normalization.neutralSummary);
                    String managerName = tuple.get(user.displayName);

                    ComplaintResponse dto = new ComplaintResponse(c);
                    dto.setNeutralSummary(summary); // 요약문 주입
                    dto.setManagerName(managerName);
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
                        similarity.as("score")
                ))
                .from(normalization)
                .join(normalization.complaint, complaint)
                .where(normalization.isCurrent.isTrue())
                .orderBy(similarity.desc())
                .limit(limit)
                .fetch();
    }

    // --- 조건 메서드 ---
    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isEmpty()) return null;
        return complaint.title.contains(keyword)
                .or(complaint.body.contains(keyword));
    }

    private BooleanExpression statusEq(ComplaintStatus status) {
        return status != null ? complaint.status.eq(status) : null;
    }

    private BooleanExpression urgencyEq(UrgencyLevel urgency) {
        return urgency != null ? complaint.urgency.eq(urgency) : null;
    }

    private BooleanExpression hasIncident(Boolean hasIncident) {
        if (hasIncident == null) return null;
        return hasIncident ? complaint.incident.isNotNull() : complaint.incident.isNull();
    }

    // --- 정렬 메서드 (Sort) ---
    private OrderSpecifier<?> getOrderSpecifier(String sort) {
        if ("urgency".equals(sort)) {
            return complaint.urgency.desc();
        } else if ("status".equals(sort)) {
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

        // [수정] 자식 민원까지 Fetch Join하여 조회
        // QueryDSL에서 OneToMany fetch join은 데이터 뻥튀기 주의가 필요하지만,
        // 여기서는 단건 조회(where id eq)이므로 distinct()를 사용하여 깔끔하게 가져옵니다.

        // 1. 사건에 묶인 민원 수 계산 (SubQuery)
        var incidentCountSubQuery = JPAExpressions
                .select(complaint.count())
                .from(complaint)
                .where(complaint.incident.id.eq(incident.id));

        // 2. 조인 쿼리 실행
        Complaint c = queryFactory
                .select(complaint)
                .from(complaint)
                .leftJoin(complaint.childComplaints).fetchJoin() // [신규] 자식들까지 한방에
                .leftJoin(complaint.incident, incident).fetchJoin()
                .where(complaint.id.eq(complaintId))
                .fetchOne(); // 여기서 distinct는 Entity Graph상 자동 처리될 수 있으나 필요시 .distinct()

        if (c == null) {
            return null;
        }

        // 3. 연관 데이터 별도 조회 (Normalization, Department, User)
        // Entity 조회 후 DTO 변환 시점에 필요한 데이터들

        // Normalization (Parent Only)
        ComplaintNormalization n = queryFactory
                .selectFrom(normalization)
                .where(normalization.complaint.eq(c))
                .fetchFirst(); // OneToOne or ManyToOne

        // 부서명
        String deptName = null;
        if (c.getCurrentDepartmentId() != null) {
            deptName = queryFactory
                    .select(department.name)
                    .from(department)
                    .where(department.id.eq(c.getCurrentDepartmentId()))
                    .fetchOne();
        }

        // 담당자 이름
        String mgrName = null;
        if (c.getAnsweredBy() != null) {
            mgrName = queryFactory
                    .select(user.displayName)
                    .from(user)
                    .where(user.id.eq(c.getAnsweredBy()))
                    .fetchOne();
        }

        // 사건 카운트
        Long iCount = 0L;
        if (c.getIncident() != null) {
            iCount = queryFactory
                    .select(complaint.count())
                    .from(complaint)
                    .where(complaint.incident.eq(c.getIncident()))
                    .fetchOne();
        }

        ComplaintDetailResponse res = new ComplaintDetailResponse(c, n, c.getIncident(), iCount, deptName);
        res.setManagerName(mgrName);
        return res;
    }
}