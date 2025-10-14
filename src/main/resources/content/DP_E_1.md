# Dynamic Programming Example - Fibonacci Optimization

Let's explore how Dynamic Programming transforms the Fibonacci sequence from exponentially slow to lightning fast!

## The Problem

Calculate the nth Fibonacci number, where:
- F(0) = 0
- F(1) = 1
- F(n) = F(n-1) + F(n-2)

## Approach 1: Naive Recursion (Inefficient)

```python
def fibonacci_naive(n):
    """Naive recursive implementation - VERY SLOW!"""
    if n <= 1:
        return n
    return fibonacci_naive(n - 1) + fibonacci_naive(n - 2)

# Test it
print(fibonacci_naive(10))  # 55 (takes microseconds)
print(fibonacci_naive(35))  # 9227465 (takes seconds!)
# fibonacci_naive(50)  # Would take hours!
```

**Why so slow?**
The same values are recalculated thousands of times:
```
fibonacci(5) calls:
  fibonacci(4) + fibonacci(3)
    fibonacci(3) + fibonacci(2) + fibonacci(2) + fibonacci(1)
      ... fibonacci(3) calculated TWICE
      ... fibonacci(2) calculated THREE times
```

**Time Complexity:** O(2^n) - doubles with each increment of n!

## Approach 2: Top-Down DP (Memoization)

```python
def fibonacci_memo(n, memo=None):
    """Top-down DP with memoization - Much faster!"""
    if memo is None:
        memo = {}

    # Check if already computed
    if n in memo:
        return memo[n]

    # Base cases
    if n <= 1:
        return n

    # Compute and store result
    memo[n] = fibonacci_memo(n - 1, memo) + fibonacci_memo(n - 2, memo)
    return memo[n]

# Test it
print(fibonacci_memo(10))   # 55 (instant)
print(fibonacci_memo(35))   # 9227465 (instant!)
print(fibonacci_memo(100))  # 354224848179261915075 (still instant!)
```

**Why faster?**
Each Fibonacci number is calculated exactly once and reused:
```
fibonacci_memo(5) with memo:
  Call fib(5) → compute → store in memo
    Call fib(4) → compute → store
      Call fib(3) → compute → store
        Call fib(2) → compute → store
          Call fib(1) → return 1
          Call fib(0) → return 0
        Return memo[2] = 1
      Call fib(2) → FOUND IN MEMO! Return 1
    Return memo[4] = 3
  Call fib(3) → FOUND IN MEMO! Return 2
Return memo[5] = 5
```

**Time Complexity:** O(n) - each subproblem solved once
**Space Complexity:** O(n) - for memo dictionary and call stack

## Approach 3: Bottom-Up DP (Tabulation)

```python
def fibonacci_tabulation(n):
    """Bottom-up DP with tabulation - Fastest!"""
    if n <= 1:
        return n

    # Create table to store results
    dp = [0] * (n + 1)
    dp[0] = 0
    dp[1] = 1

    # Build table from bottom up
    for i in range(2, n + 1):
        dp[i] = dp[i - 1] + dp[i - 2]

    return dp[n]

# Test it
print(fibonacci_tabulation(10))   # 55
print(fibonacci_tabulation(35))   # 9227465
print(fibonacci_tabulation(100))  # 354224848179261915075
```

**Why even better?**
- No recursion overhead
- Linear iteration through the table
- All values computed in order

**Time Complexity:** O(n)
**Space Complexity:** O(n) - for table only (no call stack!)

## Approach 4: Space-Optimized DP

```python
def fibonacci_optimized(n):
    """Space-optimized DP - only track last two values"""
    if n <= 1:
        return n

    # Only need previous two values
    prev2 = 0  # F(0)
    prev1 = 1  # F(1)

    for i in range(2, n + 1):
        current = prev1 + prev2
        prev2 = prev1
        prev1 = current

    return prev1

# Test it
print(fibonacci_optimized(10))   # 55
print(fibonacci_optimized(35))   # 9227465
print(fibonacci_optimized(100))  # 354224848179261915075
```

**Time Complexity:** O(n)
**Space Complexity:** O(1) - only two variables!

## Performance Comparison

For n = 35:

| Approach | Time | Space | Notes |
|----------|------|-------|-------|
| Naive | ~5 seconds | O(n) call stack | Exponential growth |
| Memoization | <1ms | O(n) | Easy to implement |
| Tabulation | <1ms | O(n) | No recursion |
| Optimized | <1ms | O(1) | Best space efficiency |

## Key Takeaway

Dynamic Programming transformed a problem from **completely impractical** (hours for n=50) to **instant** (milliseconds for n=1000+).

## Try It Yourself

1. Implement DP solution for climbing stairs: You can climb 1 or 2 steps at a time. How many ways to reach step n?
2. Create a DP solution for calculating the sum of first n natural numbers
3. Solve the "House Robber" problem: Given array of house values, you can't rob adjacent houses. Maximize stolen value.
