import requests
import threading
import time

# Settings: change as needed for your environment
BASE_URL = "http://localhost:8080"
LOGIN_ENDPOINT = "/auth/login"
SHORTCODE_ENDPOINT = "/7"  # Replace with an actual code you know exists
TEST_IP = "9.8.7.6"  # Used for X-Forwarded-For header to simulate client IP

HEADERS = {
    "Content-Type": "application/json",
    "X-Forwarded-For": TEST_IP
}

def login_test():
    url = BASE_URL + LOGIN_ENDPOINT
    data = {"username": "test", "password": "test"}
    for i in range(15):  # 15 > 10 (rate limit is 10/min)
        resp = requests.post(url, json=data, headers=HEADERS)
        print(f"LOGIN {i+1}: {resp.status_code}, {resp.text.strip()}")

def redirect_test():
    url = BASE_URL + SHORTCODE_ENDPOINT
    headers = {"X-Forwarded-For": TEST_IP}
    for i in range(105):  # 105 > 100 (rate limit is 100/min)
        resp = requests.get(url, headers=headers, allow_redirects=False)
        print(f"GET {i+1}: {resp.status_code}, {resp.text.strip()}")

def threaded_runner(target_func):
    threads = []
    for _ in range(15):
        t = threading.Thread(target=target_func)
        t.start()
        threads.append(t)
        time.sleep(0.05)  # Small stagger prevents local connection errors
    for t in threads:
        t.join()

if __name__ == "__main__":
    print("=== Burst-testing LOGIN endpoint (expecting limit at 10/min) ===")
    login_test()
    print("\nSleeping 5 seconds before redirect test...\n")
    time.sleep(5)
    print("=== Burst-testing REDIRECT endpoint (expecting limit at 100/min) ===")
    redirect_test()
