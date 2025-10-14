# Recursion - Theory

A **recursive function** is a function that calls itself during its execution. Recursion is a powerful programming technique that can simplify complex problems by breaking them into smaller, similar subproblems.

## Core Components

Every recursive function must have two essential parts:

### 1. Base Case
The condition that stops the recursion. Without a base case, the function would call itself infinitely, leading to a stack overflow error.

### 2. Recursive Case
The part where the function calls itself with a modified input, gradually moving toward the base case.

## How Recursion Works

When a recursive function is called, each invocation creates a new **stack frame** containing:
- The function's local variables
- The parameters passed to that specific call
- A return address (where to continue after the function completes)

These stack frames accumulate in memory as recursive calls are made, and are removed as the function returns, working backward through the call chain.

## Key Principles

- **Each recursive call creates a new stack frame** with its own local variables
- **The base case prevents infinite recursion** by providing a stopping condition
- **Results are combined** as the recursion "unwinds" back up the call stack
- **Memory usage grows** with recursion depth - deep recursion can cause stack overflow

## When to Use Recursion

Recursion is particularly elegant for problems with:
- Tree or graph traversals
- Divide-and-conquer algorithms
- Mathematical sequences (Fibonacci, factorials)
- Nested data structures
- Problems that can be naturally divided into similar subproblems

## Recursion vs Iteration

While any recursive solution can be rewritten iteratively, recursion often provides clearer, more maintainable code for inherently recursive problems. However, iteration may be more memory-efficient for problems with deep recursion levels.
