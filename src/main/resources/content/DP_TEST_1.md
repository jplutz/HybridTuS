# Self-Assessment: Dynamic Programming

Test your understanding of dynamic programming concepts, memoization, and optimization techniques.

## Questions

### 1. What is Dynamic Programming?
Explain what dynamic programming is and when it should be used.

**Answer:** Dynamic programming is an optimization technique that solves complex problems by breaking them into overlapping subproblems, solving each subproblem once, and storing the results. It should be used when a problem has:
1. Optimal substructure (optimal solution contains optimal solutions to subproblems)
2. Overlapping subproblems (same subproblems are solved multiple times)

### 2. Memoization vs Tabulation
What's the difference between memoization and tabulation in dynamic programming?

**Answer:**
- **Memoization (Top-Down)**: Recursive approach that stores results in a cache as needed. Starts with the main problem and breaks it down.
- **Tabulation (Bottom-Up)**: Iterative approach that fills a table systematically from smallest subproblems upward. More space-efficient and avoids recursion overhead.

### 3. Fibonacci Optimization
The naive recursive Fibonacci has O(2^n) time complexity. How does DP improve this?

**Answer:** DP reduces Fibonacci to O(n) time by storing previously computed values. Instead of recalculating fibonacci(n-1) and fibonacci(n-2) repeatedly, we store each result once and reuse it. This eliminates redundant calculations in the recursion tree.

### 4. Space Optimization
Given this memoized Fibonacci function, can you optimize the space complexity?
```python
def fib_memo(n, memo={}):
    if n <= 1:
        return n
    if n not in memo:
        memo[n] = fib_memo(n-1, memo) + fib_memo(n-2, memo)
    return memo[n]
```

**Answer:** Yes! Since we only need the last two values, we can use two variables instead of an array:
```python
def fib_optimized(n):
    if n <= 1:
        return n
    prev, curr = 0, 1
    for i in range(2, n + 1):
        prev, curr = curr, prev + curr
    return curr
```
This reduces space from O(n) to O(1).

### 5. Identifying DP Problems
Which of these problems can be solved with dynamic programming?
- Fibonacci sequence
- Binary search
- Longest common subsequence
- Finding a specific element in unsorted array
- Knapsack problem

**Answer:**
- **Yes**: Fibonacci (overlapping subproblems), Longest common subsequence (overlapping subproblems), Knapsack (optimal substructure)
- **No**: Binary search (no overlapping subproblems), Finding element (no optimal substructure or overlapping subproblems)

### 6. Trade-offs
What are the trade-offs of using dynamic programming?

**Answer:**
- **Benefits**: Dramatically improved time complexity, guaranteed optimal solution
- **Costs**: Increased space complexity (storing subproblem solutions), complexity in implementation, may be overkill for small inputs where naive solutions are faster

### 7. Climbing Stairs Problem
You're climbing stairs and can take 1 or 2 steps at a time. How many distinct ways can you climb n stairs? Explain the DP approach.

**Answer:** This is a Fibonacci variant. For n stairs:
- Base: 1 stair = 1 way, 2 stairs = 2 ways
- Recurrence: ways(n) = ways(n-1) + ways(n-2)
- You can reach stair n from either (n-1) or (n-2), so sum both possibilities
```python
def climbStairs(n):
    if n <= 2:
        return n
    prev, curr = 1, 2
    for i in range(3, n + 1):
        prev, curr = curr, prev + curr
    return curr
```

## Reflection

Think about:
- Can you recognize when a problem has overlapping subproblems?
- Do you understand the difference between recursive and iterative DP approaches?
- What problems have you encountered that could benefit from DP optimization?
