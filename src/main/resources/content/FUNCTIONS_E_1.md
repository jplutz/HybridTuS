# Functions Example - Calculator

Let's build a simple calculator using functions to demonstrate code reuse and modularity.

## Basic Operations

Define individual functions for each operation:

```python
def add(a, b):
    """Add two numbers and return the result."""
    return a + b

def subtract(a, b):
    """Subtract b from a and return the result."""
    return a - b

def multiply(a, b):
    """Multiply two numbers and return the result."""
    return a * b

def divide(a, b):
    """Divide a by b and return the result."""
    if b == 0:
        return "Error: Division by zero"
    return a / b
```

## Using the Functions

```python
# Basic usage
result1 = add(10, 5)        # 15
result2 = subtract(10, 5)   # 5
result3 = multiply(10, 5)   # 50
result4 = divide(10, 5)     # 2.0

print(f"10 + 5 = {result1}")
print(f"10 - 5 = {result2}")
print(f"10 * 5 = {result3}")
print(f"10 / 5 = {result4}")
```

**Output:**
```
10 + 5 = 15
10 - 5 = 5
10 * 5 = 50
10 / 5 = 2.0
```

## Building a Calculator Interface

Create a higher-level function that uses the basic operations:

```python
def calculator(operation, a, b):
    """
    Perform a calculation based on the operation.

    Args:
        operation (str): One of 'add', 'subtract', 'multiply', 'divide'
        a (float): First number
        b (float): Second number

    Returns:
        float or str: Result of the operation or error message
    """
    if operation == 'add':
        return add(a, b)
    elif operation == 'subtract':
        return subtract(a, b)
    elif operation == 'multiply':
        return multiply(a, b)
    elif operation == 'divide':
        return divide(a, b)
    else:
        return "Error: Unknown operation"

# Using the calculator
print(calculator('add', 15, 7))       # 22
print(calculator('multiply', 6, 8))   # 48
print(calculator('divide', 20, 0))    # Error: Division by zero
```

## Advanced: Chain Calculations

Functions can call other functions to build complex operations:

```python
def average(numbers):
    """Calculate the average of a list of numbers."""
    if len(numbers) == 0:
        return 0
    total = sum(numbers)
    count = len(numbers)
    return divide(total, count)

def variance(numbers):
    """Calculate the variance of a list of numbers."""
    if len(numbers) == 0:
        return 0

    avg = average(numbers)
    squared_diffs = []

    for num in numbers:
        diff = subtract(num, avg)
        squared_diff = multiply(diff, diff)
        squared_diffs.append(squared_diff)

    return average(squared_diffs)

# Example usage
data = [10, 20, 30, 40, 50]
print(f"Average: {average(data)}")      # Average: 30.0
print(f"Variance: {variance(data)}")    # Variance: 200.0
```

## Key Takeaways

1. **Reusability**: `add()`, `subtract()`, etc. are used in multiple places
2. **Composition**: `average()` uses `divide()`, `variance()` uses both `average()` and `subtract()`
3. **Maintainability**: If we need to change how division works, we only update one function
4. **Testing**: Each function can be tested independently

## Try It Yourself

1. Add a `power(base, exponent)` function to calculate base^exponent
2. Create a `percentage(part, whole)` function
3. Build a `circle_area(radius)` function that uses `multiply()` and π
