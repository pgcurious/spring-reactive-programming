#!/bin/bash

# Load Test Script for Blocking Demo
# Chapter 1: The Blocking World We Live In

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║            BLOCKING DEMO - LOAD TEST SCRIPT                    ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║  This script demonstrates thread exhaustion                    ║"
echo "║  Target: $BASE_URL"
echo "╚════════════════════════════════════════════════════════════════╝"
echo

# Check if server is running
echo "Step 1: Checking if server is running..."
if ! curl -s "$BASE_URL/api/health" > /dev/null 2>&1; then
    echo "ERROR: Server not responding at $BASE_URL"
    echo "Please start the blocking-demo application first."
    exit 1
fi
echo "✓ Server is running"
echo

# Check thread pool status
echo "Step 2: Current thread pool status..."
curl -s "$BASE_URL/api/threads" | python3 -m json.tool 2>/dev/null || curl -s "$BASE_URL/api/threads"
echo
echo

# Function to make concurrent requests
make_concurrent_requests() {
    local url=$1
    local count=$2
    local delay_param=$3

    echo "Making $count concurrent requests to $url..."
    for i in $(seq 1 $count); do
        if [ -n "$delay_param" ]; then
            curl -s "$url?delayMs=$delay_param" &
        else
            curl -s "$url" > /dev/null &
        fi
    done
    wait
}

# Test 1: Simple load
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 1: Simple Load - 10 concurrent requests to /api/users/1"
echo "═══════════════════════════════════════════════════════════════"
echo

start_time=$(date +%s.%N)
make_concurrent_requests "$BASE_URL/api/users/1" 10
end_time=$(date +%s.%N)
duration=$(echo "$end_time - $start_time" | bc)
echo
echo "✓ Completed in $duration seconds"
echo

sleep 1

# Test 2: Thread exhaustion
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 2: Thread Exhaustion - 15 concurrent slow requests"
echo "         (With max 10 threads, 5 will wait)"
echo "═══════════════════════════════════════════════════════════════"
echo

echo "Starting 15 requests that each block for 3 seconds..."
echo "Watch the logs for thread assignment!"
echo

start_time=$(date +%s.%N)
for i in $(seq 1 15); do
    curl -s "$BASE_URL/api/slow?delayMs=3000" &
    sleep 0.1  # Slight delay to spread out requests
done
wait
end_time=$(date +%s.%N)
duration=$(echo "$end_time - $start_time" | bc)

echo
echo "✓ All requests completed"
echo "  Total time: $duration seconds"
echo "  Expected: ~6 seconds (2 batches of 10 threads × 3s delay)"
echo "  If close to 6s, thread exhaustion occurred!"
echo

sleep 1

# Test 3: Mixed load
echo "═══════════════════════════════════════════════════════════════"
echo "TEST 3: Mixed Load - Slow requests blocking fast requests"
echo "═══════════════════════════════════════════════════════════════"
echo

echo "Starting 10 slow requests (5s each)..."
for i in $(seq 1 10); do
    curl -s "$BASE_URL/api/slow?delayMs=5000" &
done

sleep 1  # Let slow requests occupy threads

echo "Now trying to make a FAST request while threads are blocked..."
echo
start_time=$(date +%s.%N)
curl -s "$BASE_URL/api/fast"
end_time=$(date +%s.%N)
duration=$(echo "$end_time - $start_time" | bc)
echo
echo "Fast request took: $duration seconds"
echo "If > 0.1s, it was waiting for a thread!"
echo

wait  # Wait for slow requests to complete

# Summary
echo
echo "═══════════════════════════════════════════════════════════════"
echo "                        SUMMARY                                 "
echo "═══════════════════════════════════════════════════════════════"
echo
echo "Key observations from these tests:"
echo
echo "1. With 10 threads and blocking calls, throughput is limited"
echo "2. Slow requests block the entire system"
echo "3. Fast requests must wait when all threads are busy"
echo "4. Thread count is the scalability bottleneck"
echo
echo "This is the fundamental problem reactive programming solves!"
echo
