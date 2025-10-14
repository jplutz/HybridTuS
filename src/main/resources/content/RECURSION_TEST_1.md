# Self-Assessment: Recursion

Test your understanding of recursive functions and recursive problem solving.

## Questions

### 1. Base Case
What is a base case in recursion? Why is it necessary?

**Answer:** A base case is a condition that stops the recursion without making further recursive calls. It's necessary to prevent infinite recursion and provide a direct answer for the simplest input.

### 2. Factorial
Trace the execution of this recursive factorial function for `factorial(3)`:
```python
def factorial(n):
    if n <= 1:
        return 1
    return n * factorial(n-1)
```

**Answer:**
```
factorial(3)
  = 3 * factorial(2)
  = 3 * (2 * factorial(1))
  = 3 * (2 * 1)
  = 3 * 2
  = 6
```

### 3. Recursive Call
What happens during a recursive function call? Explain the call stack.

**Answer:** Each recursive call creates a new stack frame with its own local variables. These frames stack up until the base case is reached, then they unwind as each call returns its result to the previous call.

### 4. Infinite Recursion
What causes infinite recursion? How can you prevent it?

**Answer:** Infinite recursion occurs when:
1. There's no base case
2. The recursive call doesn't move toward the base case
3. The base case condition is never met

Prevent it by ensuring a proper base case and that each recursive call progresses toward it.

### 5. Iteration vs Recursion
Compare a recursive solution to an iterative solution. When is recursion preferable?

**Answer:** Recursion is more elegant for naturally recursive problems (tree traversal, divide-and-conquer). Iteration is often more efficient (less memory overhead). Use recursion when it makes the solution clearer and the recursion depth is manageable.

### 6. Fibonacci
Write a recursive function to calculate the nth Fibonacci number.

**Answer:**
```python
def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)
```

### 7. Recursion Problems
What types of problems are naturally suited for recursive solutions?

**Answer:**
- Tree/graph traversal
- Divide-and-conquer algorithms (merge sort, quick sort)
- Backtracking problems (maze solving, N-queens)
- Mathematical sequences (factorial, Fibonacci)
- Problems with self-similar sub-problems

## Reflection

Consider:
- Can you identify the base case and recursive case in any recursive function?
- Do you understand how the call stack works?
- What are the advantages and disadvantages of recursion?
