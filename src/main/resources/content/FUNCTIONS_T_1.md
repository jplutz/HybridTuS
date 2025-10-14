# Functions & Abstraction - Theory

A **function** is a reusable block of code that performs a specific task. Functions are one of the most fundamental concepts in programming, enabling code reuse, modularity, and abstraction.

## What is a Function?

A function is like a recipe: you give it ingredients (inputs), it follows a series of steps, and produces a result (output).

### Basic Anatomy:

```python
def function_name(parameters):
    # Function body
    # Code to execute
    return result
```

## Key Components

1. **Function Name**: Descriptive identifier (e.g., `calculate_total`, `find_max`)
2. **Parameters**: Input values the function accepts (optional)
3. **Function Body**: The code that executes when the function is called
4. **Return Value**: The result the function produces (optional)

## Why Use Functions?

### 1. Code Reuse
Write once, use many times:
```python
def greet(name):
    return f"Hello, {name}!"

print(greet("Alice"))  # Hello, Alice!
print(greet("Bob"))    # Hello, Bob!
```

### 2. Abstraction
Hide complex details behind a simple interface:
```python
# You don't need to know HOW it sorts, just that it does
sorted_list = sorted([3, 1, 4, 1, 5, 9])
```

### 3. Modularity
Break large problems into smaller, manageable pieces:
```python
def validate_email(email):
    # Validation logic
    pass

def send_email(to, subject, body):
    if validate_email(to):
        # Send logic
        pass
```

### 4. Testability
Easy to test individual pieces of functionality:
```python
def add(a, b):
    return a + b

# Easy to test
assert add(2, 3) == 5
```

## Types of Functions

### Pure Functions
Always produce the same output for the same input, no side effects:
```python
def multiply(a, b):
    return a * b  # Pure: always same result for same inputs
```

### Functions with Side Effects
Modify external state or interact with I/O:
```python
def log_message(msg):
    print(msg)  # Side effect: outputs to console
    with open('log.txt', 'a') as f:
        f.write(msg)  # Side effect: modifies file system
```

## Parameters vs Arguments

- **Parameters**: Variables in the function definition
- **Arguments**: Actual values passed when calling the function

```python
def power(base, exponent):  # base and exponent are parameters
    return base ** exponent

result = power(2, 3)  # 2 and 3 are arguments
```

## Return Values

Functions can return:
- **Single value**: `return 42`
- **Multiple values**: `return x, y, z` (as a tuple)
- **Nothing**: `return None` (or just `return`, or no return statement)

## Function Scope

Variables inside functions are **local** to that function:
```python
def my_function():
    x = 10  # Local variable
    print(x)

my_function()  # 10
print(x)  # Error: x is not defined outside the function
```

## Best Practices

1. **Single Responsibility**: Each function should do one thing well
2. **Descriptive Names**: Use verbs that describe what the function does
3. **Keep It Short**: Ideally under 20-30 lines
4. **Avoid Side Effects**: When possible, prefer pure functions
5. **Document**: Use docstrings to explain complex functions

Functions are the building blocks of larger programs. Mastering them is essential for writing clean, maintainable code!
