# Self-Assessment: Functions & Abstractions

Test your understanding of functions, parameters, return values, and abstraction.

## Questions

### 1. Function Definition
Write a function called `greet` that takes a name as a parameter and returns "Hello, [name]!"

**Answer:**
```python
def greet(name):
    return f"Hello, {name}!"
```

### 2. Return Values
What is the difference between `print()` and `return` in a function?

**Answer:** `print()` displays output to the console but doesn't send a value back. `return` sends a value back to the caller that can be stored or used in expressions.

### 3. Parameters
What will this function return?
```python
def add_numbers(a, b=5):
    return a + b

result = add_numbers(10)
```

**Answer:** 15 (a=10, b uses default value of 5, so 10+5=15)

### 4. Scope
What is variable scope? What happens to variables defined inside a function?

**Answer:** Scope determines where a variable can be accessed. Variables defined inside a function are local to that function and cannot be accessed outside it.

### 5. Multiple Returns
Can a function return multiple values? If so, how?

**Answer:** Yes, a function can return multiple values by returning a tuple:
```python
def get_coordinates():
    return 10, 20  # Returns (10, 20)

x, y = get_coordinates()
```

### 6. Abstraction
Why is abstraction important in programming? Give an example of how a function provides abstraction.

**Answer:** Abstraction hides complex implementation details behind a simple interface. Example: `calculate_tax(price)` hides the tax calculation formula, letting users call it without knowing the math inside.

### 7. Function Composition
Write a function that calls another function. Explain the flow of execution.

**Answer:**
```python
def double(x):
    return x * 2

def double_and_add_five(x):
    result = double(x)  # Calls double first
    return result + 5   # Then adds 5
```
Execution: double_and_add_five calls double, gets the result, then adds 5.

## Reflection

Reflect on:
- How do functions help organize code?
- When should you create a new function?
- What makes a good function name and design?
