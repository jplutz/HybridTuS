# Dynamic Programming - Theory

**Dynamic Programming (DP)** is an optimization technique that solves complex problems by breaking them down into simpler subproblems. It's particularly powerful when the same subproblems occur multiple times.

## Core Idea

Instead of solving the same subproblem repeatedly, DP solves each subproblem once and stores the result for future use. This trade-off exchanges memory for speed.

### The Inefficient Way (Naive Recursion):

```python
def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

# fibonacci(5) recalculates fibonacci(3) multiple times!
```

### The DP Way:

```python
def fibonacci_dp(n):
    memo = {0: 0, 1: 1}
    for i in range(2, n + 1):
        memo[i] = memo[i-1] + memo[i-2]
    return memo[n]

# Each fibonacci value calculated exactly once!
```

## When to Use Dynamic Programming

DP is applicable when a problem has:

1. **Overlapping Subproblems**: The same subproblems are solved multiple times
2. **Optimal Substructure**: The optimal solution can be constructed from optimal solutions of subproblems

### Classic Examples:
- Fibonacci sequence
- Shortest path problems
- Knapsack problem
- Longest common subsequence
- Matrix chain multiplication

## Two Approaches to DP

### 1. Top-Down (Memoization)

Start with the original problem and recursively break it down, storing results:

```python
def fib_memo(n, memo={}):
    if n in memo:
        return memo[n]
    if n <= 1:
        return n

    memo[n] = fib_memo(n-1, memo) + fib_memo(n-2, memo)
    return memo[n]
```

**Characteristics:**
- Uses recursion
- Computes only needed subproblems
- More intuitive for some problems
- Risk of stack overflow for deep recursion

### 2. Bottom-Up (Tabulation)

Start with the smallest subproblems and build up to the solution:

```python
def fib_tabulation(n):
    if n <= 1:
        return n

    table = [0] * (n + 1)
    table[1] = 1

    for i in range(2, n + 1):
        table[i] = table[i-1] + table[i-2]

    return table[n]
```

**Characteristics:**
- Uses iteration
- Computes all subproblems
- More efficient (no recursion overhead)
- Better space complexity when optimized

## Time Complexity Benefits

**Naive Recursion (Fibonacci):**
- Time: O(2^n) - exponential!
- Each call branches into two more calls

**Dynamic Programming:**
- Time: O(n) - linear!
- Each subproblem solved exactly once

For `n = 40`:
- Naive: ~1 billion operations
- DP: ~40 operations

## Space-Time Tradeoff

DP uses extra memory to store subproblem results, but dramatically reduces computation time.

### Space Optimization

Often you can reduce space complexity:

```python
# Standard DP: O(n) space
def fib_standard(n):
    dp = [0] * (n + 1)
    dp[1] = 1
    for i in range(2, n + 1):
        dp[i] = dp[i-1] + dp[i-2]
    return dp[n]

# Optimized: O(1) space
def fib_optimized(n):
    if n <= 1:
        return n
    prev2, prev1 = 0, 1
    for _ in range(2, n + 1):
        current = prev1 + prev2
        prev2, prev1 = prev1, current
    return prev1
```

## Steps to Solve DP Problems

1. **Define the subproblem**: What smaller problem can you solve?
2. **Identify the recurrence relation**: How do subproblems relate?
3. **Identify base cases**: What are the simplest cases?
4. **Decide bottom-up or top-down**: Which approach fits better?
5. **Optimize space**: Can you reduce memory usage?

## Common Patterns

### 1D DP (Linear):
- Fibonacci, climbing stairs
- Array: `dp[i]` represents solution up to index `i`

### 2D DP (Grid):
- Longest common subsequence, edit distance
- Array: `dp[i][j]` represents solution for subproblem involving `i` and `j`

### 3D+ DP:
- Complex optimization problems
- Array: `dp[i][j][k]` with multiple dimensions

Dynamic Programming is a powerful paradigm that transforms intractable problems into efficient solutions. Mastering it opens the door to solving a wide range of optimization challenges!
