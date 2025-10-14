# Dynamic Programming - Optimize a Problem

## Active Learning Exercise

This activity teaches you to **transform a slow recursive solution into an optimized dynamic programming solution** through hands-on practice.

## The Problem: Climbing Stairs

You're climbing a staircase with `n` steps. Each time you can either climb **1 step or 2 steps**. How many distinct ways can you climb to the top?

**Examples:**
- For n=2: There are 2 ways: (1+1) or (2)
- For n=3: There are 3 ways: (1+1+1), (1+2), or (2+1)
- For n=4: ?

## Step 1: Solve with Naive Recursion

First, write a **recursive solution**:

```python
def climbStairs(n):
    # Base cases
    if n == 1:
        return 1
    if n == 2:
        return 2

    # Recursive case: sum of previous two
    return climbStairs(n-1) + climbStairs(n-2)
```

### Your Task: Analyze Performance

Draw the recursion tree for `climbStairs(5)`. Identify repeated calculations:

```
climbStairs(5)
├── climbStairs(4)
│   ├── climbStairs(3)
│   │   ├── climbStairs(?)
│   │   └── climbStairs(?)
│   └── climbStairs(?)
└── climbStairs(3)  <- REPEATED!
    ├── climbStairs(?)
    └── climbStairs(?)
```

**Question:** How many times is `climbStairs(3)` calculated?

## Step 2: Optimize with Memoization

Now add **memoization** (top-down DP) to cache results:

```python
def climbStairsMemo(n, memo={}):
    # Check cache first
    if n in memo:
        return memo[n]

    # Base cases
    if n == 1:
        return 1
    if n == 2:
        return 2

    # Calculate and store in cache
    memo[n] = climbStairsMemo(n-1, memo) + climbStairsMemo(n-2, memo)
    return memo[n]
```

### Your Task: Fill the Memoization Table

For `climbStairsMemo(6)`, fill in when each value is computed:

| n | Value | Computed on call to... |
|---|-------|------------------------|
| 1 | 1     | Base case |
| 2 | 2     | Base case |
| 3 | ?     | climbStairsMemo(3) |
| 4 | ?     | climbStairsMemo(4) |
| 5 | ?     | ? |
| 6 | ?     | ? |

## Step 3: Transform to Bottom-Up DP

Convert the solution to **bottom-up dynamic programming** (no recursion):

```python
def climbStairsDP(n):
    if n == 1:
        return 1
    if n == 2:
        return 2

    # Build table from bottom up
    dp = [0] * (n + 1)
    dp[1] = 1
    dp[2] = 2

    for i in range(3, n + 1):
        dp[i] = dp[i-1] + dp[i-2]

    return dp[n]
```

### Your Task: Trace the DP Table

Fill in the DP table for n=7:

| i | dp[i] | Calculation |
|---|-------|-------------|
| 1 | 1     | Base case |
| 2 | 2     | Base case |
| 3 | ?     | dp[2] + dp[1] = ? |
| 4 | ?     | dp[?] + dp[?] = ? |
| 5 | ?     | ? |
| 6 | ?     | ? |
| 7 | ?     | ? |

## Solution

<details>
<summary>Click to reveal answers</summary>

### Memoization Table (n=6):
| n | Value | Computed on call to... |
|---|-------|------------------------|
| 1 | 1     | Base case |
| 2 | 2     | Base case |
| 3 | 3     | climbStairsMemo(3) |
| 4 | 5     | climbStairsMemo(4) |
| 5 | 8     | climbStairsMemo(5) |
| 6 | 13    | climbStairsMemo(6) |

### Bottom-Up DP Table (n=7):
| i | dp[i] | Calculation |
|---|-------|-------------|
| 1 | 1     | Base case |
| 2 | 2     | Base case |
| 3 | 3     | dp[2] + dp[1] = 2 + 1 |
| 4 | 5     | dp[3] + dp[2] = 3 + 2 |
| 5 | 8     | dp[4] + dp[3] = 5 + 3 |
| 6 | 13    | dp[5] + dp[4] = 8 + 5 |
| 7 | 21    | dp[6] + dp[5] = 13 + 8 |

</details>

## Step 4: Space Optimization Challenge

The bottom-up solution uses O(n) space. Can you optimize it to use only O(1) space?

**Hint:** You only need the last two values at any time!

Try implementing it yourself before looking at solutions online.

## Reflection Questions

1. **Performance:** Time complexity of naive recursion vs DP?
2. **Space:** Which DP approach (memoization vs bottom-up) uses more space? Why?
3. **Applicability:** What characteristics make a problem suitable for DP?

## Key Takeaway

Through active problem-solving, you learned:
- How to identify repeated subproblems
- Techniques to cache and reuse solutions
- Trade-offs between top-down and bottom-up DP
- How to optimize both time and space complexity

Dynamic programming mastery comes from practice—keep solving problems!
