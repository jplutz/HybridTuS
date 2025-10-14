# Recursion - Example

Let's explore recursion through a classic problem: **computing Fibonacci numbers**.

## The Problem

The Fibonacci sequence is a series where each number is the sum of the two preceding ones:

```
0, 1, 1, 2, 3, 5, 8, 13, 21, 34...
```

Mathematically:
- F(0) = 0
- F(1) = 1
- F(n) = F(n-1) + F(n-2) for n > 1

## Recursive Solution

```python
def fibonacci(n):
    # Base cases
    if n == 0:
        return 0
    if n == 1:
        return 1

    # Recursive case: sum of previous two numbers
    return fibonacci(n - 1) + fibonacci(n - 2)

# Calculate the 6th Fibonacci number
print(fibonacci(6))  # Output: 8
```

## Step-by-Step Execution

Let's trace `fibonacci(5)` by examining the call tree structure:

## Call Tree Breakdown

```
fibonacci(5)
├── fibonacci(4)
│   ├── fibonacci(3)
│   │   ├── fibonacci(2)
│   │   │   ├── fibonacci(1) → 1
│   │   │   └── fibonacci(0) → 0
│   │   └── fibonacci(1) → 1
│   └── fibonacci(2)
│       ├── fibonacci(1) → 1
│       └── fibonacci(0) → 0
└── fibonacci(3)
    ├── fibonacci(2)
    │   ├── fibonacci(1) → 1
    │   └── fibonacci(0) → 0
    └── fibonacci(1) → 1
```

## Another Example: Sum of Array

Here's a practical example of using recursion to sum all elements in an array:

```javascript
function sumArray(arr, index = 0) {
    // Base case: reached the end
    if (index >= arr.length) {
        return 0;
    }

    // Recursive case: current element + sum of rest
    return arr[index] + sumArray(arr, index + 1);
}

const numbers = [1, 2, 3, 4, 5];
console.log(sumArray(numbers));  // Output: 15
```

## Optimization Note

The naive Fibonacci implementation has exponential time complexity O(2^n) because it recalculates the same values many times. This can be optimized using:

- **Memoization** (caching results)
- **Dynamic programming** (bottom-up approach)
- **Iteration** (when recursion depth is a concern)

## Try It Yourself

Can you write a recursive function to:
1. Reverse a string?
2. Find the maximum value in an array?
3. Calculate the power of a number (x^n)?
