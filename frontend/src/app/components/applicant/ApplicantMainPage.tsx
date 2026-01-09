import { useEffect, useState } from 'react';
import { Toolbar } from './toolbar';
import { RecentComplaints } from './recent-complaints';
import { ResponseTimeStats } from './response-time-stats';
import { KeywordCloud } from './keyword-cloud';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import Swal from 'sweetalert2';

interface ComplaintDto {
  id: number;
  title: string;
  status: string;
  createdAt: string;
}


// TODO: Mock data - 나중에 백엔드 API 연동 시 교체 필요
const mockRecentComplaints = [
  {
    id: 'C2024-00234',
    title: '아파트 주변 가로등 고장',
    content: '서초구 반포동 123-45번지 아파트 정문 앞 가로등이 2주째 작동하지 않아 야간에 보행자 안전에 위험이 있습니다. 조속한 수리를 요청드립니다.',
    status: 'categorizing' as const,
    submittedDate: '2024-01-05',
  },
  {
    id: 'C2024-00198',
    title: '불법 주정차 단속 요청',
    content: '강남구 역삼동 주택가 이면도로에 상습적으로 불법 주정차하는 차량들로 인해 주민들의 통행에 불편을 겪고 있습니다. 단속을 강화해 주시기 바랍니다.',
    status: 'assigned' as const,
    submittedDate: '2024-01-03',
  },
  {
    id: 'C2024-00156',
    title: '공원 놀이터 시설 보수',
    content: '송파구 올림픽공원 내 어린이 놀이터의 그네 줄이 해어져 있고, 미끄럼틀 표면이 벗겨져 아이들이 다칠 위험이 있습니다. 점검 및 보수를 부탁드립니다.',
    status: 'answered' as const,
    submittedDate: '2023-12-28',
  },
  {
    id: 'C2024-00089',
    title: '도로 포트홀 신고',
    content: '마포구 상암동 월드컵북로 차선 중앙에 큰 포트홀이 발생했습니다. 차량 통행에 위험하오니 긴급 보수를 요청합니다.',
    status: 'answered' as const,
    submittedDate: '2023-12-20',
  },
];

// Mock data for response time statistics
const mockResponseTimeData = [
  { category: '도로/교통', avgDays: 3.2 },
  { category: '환경/위생', avgDays: 5.1 },
  { category: '공원/시설', avgDays: 4.5 },
  { category: '안전/치안', avgDays: 2.8 },
  { category: '기타', avgDays: 6.3 },
];

const mockOverallStats = {
  averageResponseTime: 4.4,
  fastestCategory: '안전/치안',
  improvementRate: 12,
};

// Mock data for keywords
const mockKeywords = [
  { text: '가로등', value: 45 },
  { text: '주정차', value: 38 },
  { text: '포트홀', value: 32 },
  { text: '쓰레기', value: 28 },
  { text: '소음', value: 25 },
  { text: '교통', value: 22 },
  { text: '안전', value: 20 },
  { text: '보수', value: 18 },
  { text: '보도', value: 15 },
  { text: '공원', value: 12 },
  { text: '하수구', value: 10 },
  { text: '가로수', value: 8 },
  { text: '공사', value: 7 },
  { text: '불법', value: 6 },
];

const ApplicantMainPage = () => {

  const navigate = useNavigate();

  const handleViewComplaints = () => {
    console.log('과거 민원 보기');
    navigate('/applicant/complaints');
    // Navigate to complaints list view
  };

  const handleNewComplaint = () => {
    console.log('새 민원 작성');
    navigate('/applicant/complaints/new');
    // Navigate to new complaint form
  };

  const handleLogout = () => {
    Swal.fire({
      title: '로그아웃',
      text: "정말 로그아웃 하시겠습니까?",
      icon: 'question',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: '로그아웃',
      cancelButtonText: '취소'
    }).then((result) => {
      if (result.isConfirmed) {
        localStorage.removeItem('accessToken');
        Swal.fire(
          '로그아웃 완료',
          '성공적으로 로그아웃되었습니다.',
          'success'
        ).then(() => {
          navigate('/applicant/login');
        });
      }
    });
  };

  const [recentComplaints, setRecentComplaints] = useState<ComplaintDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {

    const token = localStorage.getItem('accessToken');
    if (!token) {
      Swal.fire({
        title: '로그인 필요',
        text: '민원 서비스를 이용하기 위해서는 로그인이 필요합니다!',
        icon: 'warning',
        confirmButtonText: '로그인 하러 가기'
      }).then((result) => {
        if (result.isConfirmed) {
          navigate('/applicant/login');
        }
      });
      return;
    }

    const fetchRecentComplaints = async () => {
      try {
        const token = localStorage.getItem('accessToken');
        // 백엔드 API 호출 - 최근 3개의 민원 불러오기
        // 백엔드에서 만든 최신 3개 전용 API 호출
        const response = await axios.get('http://localhost:8080/api/applicant/complaints/top3', {
          headers: { Authorization: `Bearer ${token}` }
        });

        setRecentComplaints(response.data);
      } catch (error) {
        console.error("최신 민원 로드 실패:", error);
      } finally {
        setIsLoading(false);
      }

    };
    fetchRecentComplaints();
    // 빈 배열: 한 번만 실행, accessToken: 변경 시 재실행
  }, []);

  return (
    <div className="min-h-screen bg-gray-50">
      <Toolbar
        onViewComplaints={handleViewComplaints}
        onNewComplaint={handleNewComplaint}
        onLogout={handleLogout}
      />

      <main className="max-w-7xl mx-auto px-6 py-8">
        <div className="space-y-8">
          {/* Recent Complaints */}
          {isLoading ? (<p>데이터를 불러오는 중입니다...</p>) : Array.isArray(recentComplaints) && recentComplaints.length > 0 ?
            (
              recentComplaints.map((complaint) => (
                <div key={complaint.id} className="complaint-card">
                  <h4>{complaint.title}</h4>
                  <span>{complaint.status}</span>
                  <p>{complaint.createdAt}</p>
                </div>
              ))
            ) :
            (
              <div className="bg-white border-2 border-dashed border-gray-200 rounded-2xl py-12 flex flex-col items-center justify-center shadow-sm">
                <div className="bg-gray-50 p-4 rounded-full mb-4">
                  {/* 서류 아이콘 같은 느낌의 이모지 혹은 아이콘 */}
                  <span className="text-3xl">📄</span>
                </div>
                <h3 className="text-xl font-bold text-gray-700 mb-2">최근 신청한 민원이 없습니다</h3>
                <p className="text-gray-400">새로운 민원을 작성하여 불편사항을 해결해 보세요.</p>
                <button
                  onClick={handleNewComplaint}
                  className="mt-6 text-blue-600 font-semibold hover:underline"
                >
                  + 새 민원 작성하기
                </button>
              </div>
            )}

          {/* Stats and Keywords Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            <ResponseTimeStats
              data={mockResponseTimeData}
              overallStats={mockOverallStats}
            />
            <KeywordCloud keywords={mockKeywords} />
          </div>
        </div>
      </main>
    </div>
  );
}

export default ApplicantMainPage;