import React, { useState, useEffect } from 'react';
import KakaoMap from './applicant/KakaoMap'; // 경로 확인 필요
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

const HeatmapPage = () => {
    const [complaints, setComplaints] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();

    // 1. 강동구 중심 데이터 로드
    useEffect(() => {
        const fetchHeatmapData = async () => {
            try {
                setIsLoading(true);
                // 백엔드에서 위경도(lat, lon)가 포함된 민원 목록을 가져옵니다.
                const response = await axios.get('/applicant/heatmap');
                setComplaints(response.data);
            } catch (error) {
                console.error("데이터 로드 실패:", error);
            } finally {
                setIsLoading(false);
            }
        };
        fetchHeatmapData();
    }, []);

    const handleViewDetail = (id: string) => {
        navigate(`/admin/complaints/${id}`);
    };

    return (
        // 페이지 전체를 차지하는 컨테이너
        <div className="w-full h-screen bg-gray-100 flex flex-col overflow-hidden">

            {/* (선택사항) 지도 위 플로팅 타이틀이나 필터 바 */}
            <div className="absolute top-6 left-6 z-10 bg-white/90 backdrop-blur shadow-lg rounded-2xl p-4 border border-gray-200">
                <h1 className="text-xl font-bold text-gray-800">강동구 민원 밀집도 분석</h1>
                <p className="text-sm text-gray-500">실시간 클러스터링 기반 히트맵</p>
            </div>

            {/* 지도 영역: 부모의 남은 공간을 100% 채움 */}
            <div className="flex-1 w-full h-full relative">
                {isLoading ? (
                    <div className="absolute inset-0 flex items-center justify-center bg-white/50 z-20">
                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
                    </div>
                ) : (
                    <KakaoMap
                        complaints={complaints}
                        mapView="heatmap" // 기본값을 히트맵(클러스터)으로 설정
                        onViewDetail={handleViewDetail}
                    />
                )}
            </div>
        </div>
    );
};

export default HeatmapPage;