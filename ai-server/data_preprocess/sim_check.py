import psycopg2
import requests

# DB 및 Ollama 설정
DB_CONFIG = {"host": "localhost", "database": "postgres", "user": "postgres", "password": "0000"}
OLLAMA_URL = "http://localhost:11434/api/embeddings"
EMBED_MODEL = "mxbai-embed-large"

def get_embedding(text):
    # mxbai 모델 권장 접두사 추가 테스트
    # text = f"query: {text}" 
    payload = {"model": EMBED_MODEL, "prompt": text}
    res = requests.post(OLLAMA_URL, json=payload).json()
    return res.get('embedding') or res.get('embeddings', [[]])[0]

def deep_inspect():
    test_text = "집 앞 불법 주정차 차량 2주 방치에 대한 빠른 처리 촉구. 불법주정차, 장기방치, 처리지연, 민원불만, 신속처리 교통지도과"
    query_vec = get_embedding(test_text)
    
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    
    # 상위 2개의 실제 벡터 값을 가져옵니다.
    cur.execute("SELECT topic, embedding FROM complaint_normalizations LIMIT 2;")
    rows = cur.fetchall()
    
    print(f"\n[1] 검색어 벡터 (앞부분 5개): {query_vec[:5]}")
    
    for i, (topic, db_vec) in enumerate(rows):
        # db_vec는 이미 문자열 '[0.1, 0.2...]' 형태일 수 있으므로 파싱 필요할 수 있음
        # pgvector는 보통 리스트 형태로 반환합니다.
        print(f"\n[DB 데이터 {i+1}] 주제: {topic}")
        print(f"    벡터 값 (앞부분 5개): {db_vec[:5]}")
        
        # 직접 비교
        is_same = (query_vec[:10] == db_vec[:10])
        print(f"    검색어와 앞부분 10개 일치 여부: {is_same}")

    # SQL 연산 결과 확인
    cur.execute("SELECT 1 - (embedding <=> %s::vector) FROM complaint_normalizations LIMIT 1;", (query_vec,))
    score = cur.fetchone()[0]
    print(f"\n[3] SQL 직접 계산 유사도 점수: {score}")

    cur.close()
    conn.close()

if __name__ == "__main__":
    deep_inspect()