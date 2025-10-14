# Recursion Example: Factorial

The factorial of a number n (written as n!) is the product of all positive integers less than or equal to n. This is a classic example to understand recursion.

## Mathematical Definition

```
n! = n × (n-1) × (n-2) × ... × 2 × 1
0! = 1 (by definition)
1! = 1
```

Examples:
- 5! = 5 × 4 × 3 × 2 × 1 = 120
- 3! = 3 × 2 × 1 = 6

## Recursive Implementation

```python
def factorial(n):
    # Base case: factorial of 0 or 1 is 1
    if n <= 1:
        return 1

    # Recursive case: n! = n × (n-1)!
    return n * factorial(n - 1)

# Example usage
print(factorial(5))  # Output: 120
print(factorial(0))  # Output: 1
print(factorial(3))  # Output: 6
```

## Step-by-Step Execution

Let's trace what happens when we call `factorial(4)`:

1. **factorial(4)** is called
   - Check: is 4 <= 1? No
   - Return: `4 * factorial(3)`
   - **Waits** for factorial(3) to complete

2. **factorial(3)** is called
   - Check: is 3 <= 1? No
   - Return: `3 * factorial(2)`
   - **Waits** for factorial(2) to complete

3. **factorial(2)** is called
   - Check: is 2 <= 1? No
   - Return: `2 * factorial(1)`
   - **Waits** for factorial(1) to complete

4. **factorial(1)** is called
   - Check: is 1 <= 1? **Yes!** (Base case reached)
   - Return: `1`

Now the recursion "unwinds":

5. **factorial(2)** receives 1, computes: `2 × 1 = 2`, returns 2
6. **factorial(3)** receives 2, computes: `3 × 2 = 6`, returns 6
7. **factorial(4)** receives 6, computes: `4 × 6 = 24`, returns 24

**Final result: 24**

## Implementation in Other Languages

### JavaScript
```javascript
function factorial(n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}

console.log(factorial(5)); // 120
```

### Java
```java
public static int factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}
```

## Try It Yourself

Modify the factorial function to:
1. Add input validation (handle negative numbers)
2. Add a counter to track how many recursive calls are made
3. Implement an iterative version and compare performance
