# Dynamic Programming - Memoization Table Visualization

![Memoization Table](/figures/dp-memoization.png)

## Visualizing How DP Avoids Redundant Work

Let's see how Dynamic Programming uses a memoization table to store and reuse results.

### Problem: Calculate Fibonacci(5)

**Naive Recursion Tree** (showing redundant calculations):

```
                    fib(5)
                   /      \
              fib(4)      fib(3)
             /     \      /     \
        fib(3)   fib(2) fib(2) fib(1)
        /   \    /   \   /   \
    fib(2) fib(1) fib(1) fib(0) fib(1) fib(0)
    /   \
fib(1) fib(0)

REDUNDANT CALLS:
- fib(3): calculated 2 times
- fib(2): calculated 3 times
- fib(1): calculated 5 times
- fib(0): calculated 3 times

Total function calls: 15
```

**DP with Memoization** (showing reuse):

```
Step-by-step memo table construction:

Step 1: Base cases
┌───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ ← Index
├───┼───┼───┼───┼───┼───┤
│ 0 │ 1 │ ? │ ? │ ? │ ? │ ← Memo
└───┴───┴───┴───┴───┴───┘

Step 2: Calculate fib(2)
memo[2] = memo[1] + memo[0] = 1 + 0 = 1
┌───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │
├───┼───┼───┼───┼───┼───┤
│ 0 │ 1 │ 1 │ ? │ ? │ ? │
└───┴───┴───┴───┴───┴───┘

Step 3: Calculate fib(3)
memo[3] = memo[2] + memo[1] = 1 + 1 = 2
┌───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │
├───┼───┼───┼───┼───┼───┤
│ 0 │ 1 │ 1 │ 2 │ ? │ ? │
└───┴───┴───┴───┴───┴───┘

Step 4: Calculate fib(4)
memo[4] = memo[3] + memo[2] = 2 + 1 = 3
┌───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │
├───┼───┼───┼───┼───┼───┤
│ 0 │ 1 │ 1 │ 2 │ 3 │ ? │
└───┴───┴───┴───┴───┴───┘

Step 5: Calculate fib(5)
memo[5] = memo[4] + memo[3] = 3 + 2 = 5
┌───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │
├───┼───┼───┼───┼───┼───┤
│ 0 │ 1 │ 1 │ 2 │ 3 │ 5 │ ✓ Final answer
└───┴───┴───┴───┴───┴───┘

Total unique calculations: 6
Reused from memo: 9 times
```

## Comparison: Calls vs Lookups

### Naive Recursion
Every box represents a function call:
```
fib(5): 1 call
  └─ fib(4): 1 call
      └─ fib(3): 1 call
          └─ fib(2): 1 call
              └─ fib(1): 1 call ✓
              └─ fib(0): 1 call ✓
          └─ fib(1): 1 call ✓
      └─ fib(2): 1 call (REDUNDANT!)
          └─ fib(1): 1 call ✓
          └─ fib(0): 1 call ✓
  └─ fib(3): 1 call (REDUNDANT!)
      └─ fib(2): 1 call (REDUNDANT!)
          └─ fib(1): 1 call ✓
          └─ fib(0): 1 call ✓
      └─ fib(1): 1 call ✓

Total: 15 calls
```

### DP Memoization
Green = calculated once, Blue = retrieved from memo:
```
fib(5): Calculate
  └─ fib(4): Calculate
      └─ fib(3): Calculate
          └─ fib(2): Calculate
              └─ fib(1): Base case ✓
              └─ fib(0): Base case ✓
          └─ fib(1): Memo lookup 🔵
      └─ fib(2): Memo lookup 🔵
  └─ fib(3): Memo lookup 🔵

Calculations: 6
Memo lookups: 3
Total operations: 9 (vs 15)
```

## 2D DP Example: Grid Paths

For problems like "unique paths in a grid", DP uses a 2D table:

```
Problem: Count paths from top-left to bottom-right (only move right/down)

Grid (3x3):
┌───┬───┬───┐
│ S │   │   │  S = Start
├───┼───┼───┤
│   │   │   │
├───┼───┼───┤
│   │   │ E │  E = End
└───┴───┴───┘

DP Table (paths to reach each cell):
┌───┬───┬───┐
│ 1 │ 1 │ 1 │  First row: only 1 way (go right)
├───┼───┼───┤
│ 1 │ 2 │ 3 │  dp[i][j] = dp[i-1][j] + dp[i][j-1]
├───┼───┼───┤
│ 1 │ 3 │ 6 │  Bottom-right = total paths
└───┴───┴───┘

Answer: 6 unique paths
```

## Key Insight

The memoization table transforms the problem from a tree (with exponential redundancy) to a linear/grid structure where each subproblem is solved exactly once.

**Memory cost:** O(n) or O(n²)
**Time saved:** Exponential → Polynomial!

This is why DP is one of the most powerful optimization techniques in computer science!
