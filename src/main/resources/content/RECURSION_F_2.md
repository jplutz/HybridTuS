# Fibonacci Recursion Tree

![Recursion Tree for fibonacci(5)](/figures/fibonacci-tree.png)

## Understanding the Recursion Tree

This visualization shows the complete recursion tree for `fibonacci(5)`. Each node represents a function call, and the branches show which recursive calls it makes.

### Tree Structure:

The tree demonstrates how `fibonacci(5)` breaks down into:
- `fibonacci(4)` + `fibonacci(3)`

Each of those further breaks down:
- `fibonacci(4)` → `fibonacci(3)` + `fibonacci(2)`
- `fibonacci(3)` → `fibonacci(2)` + `fibonacci(1)`
- And so on...

### Important Insights:

**Redundant Calculations:**
Notice how some values are calculated multiple times:
- `fibonacci(3)` is calculated **2 times**
- `fibonacci(2)` is calculated **3 times**
- `fibonacci(1)` is calculated **5 times**
- `fibonacci(0)` is calculated **3 times**

**Why This Matters:**
- The naive recursive approach recalculates the same values repeatedly
- This leads to **exponential time complexity** O(2^n)
- For larger values (like n=40), this becomes extremely slow

**Optimization Strategies:**
1. **Memoization**: Cache results of previous calculations
2. **Dynamic Programming**: Build solution bottom-up
3. **Iteration**: Use a loop instead of recursion

This inefficiency is why the Fibonacci example is often used to teach **algorithm optimization** techniques, not just recursion basics.
