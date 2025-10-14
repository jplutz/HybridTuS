# Recursion Call Stack Visualization

![Call Stack for factorial(4)](/figures/recursion-stack.png)

## Understanding the Call Stack

This visualization shows how `factorial(4)` creates multiple stack frames during execution. Each box represents a function call in the call stack.

### The Process:

**Building the Stack (Going Down):**
1. `factorial(4)` is called and waits for `factorial(3)`
2. `factorial(3)` is called and waits for `factorial(2)`
3. `factorial(2)` is called and waits for `factorial(1)`
4. `factorial(1)` is called - **Base case reached!** Returns 1

**Unwinding the Stack (Coming Back Up):**
5. `factorial(2)` completes: `2 × 1 = 2`
6. `factorial(3)` completes: `3 × 2 = 6`
7. `factorial(4)` completes: `4 × 6 = 24`

### Key Observations:

- **Memory grows** as recursive calls are made
- Each function call has its own **parameter value** (n=4, n=3, n=2, n=1)
- The **base case** stops the growth and begins the unwinding
- Results are **combined** as the stack unwinds
- The **deepest call completes first**, then returns to previous calls
