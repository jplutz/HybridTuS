# Loops & Iteration - Theory

A **loop** is a programming construct that repeats a block of code multiple times. Loops are fundamental to programming, allowing you to process collections, repeat operations, and implement algorithms efficiently.

## Types of Loops

### 1. For Loop
Used when you know in advance how many times to repeat:

```python
for i in range(5):
    print(f"Iteration {i}")
```

### 2. While Loop
Used when the number of iterations depends on a condition:

```python
count = 0
while count < 5:
    print(f"Count: {count}")
    count += 1
```

### 3. Do-While Loop
Executes at least once, then checks condition (Java/C++):

```java
int count = 0;
do {
    System.out.println("Count: " + count);
    count++;
} while (count < 5);
```

## Loop Control Flow

Every loop has three key components:

1. **Initialization**: Set starting values (e.g., `i = 0`)
2. **Condition**: Test whether to continue (e.g., `i < 10`)
3. **Update**: Modify loop variables (e.g., `i++`)

## Loop Control Statements

- **break**: Exit the loop immediately
- **continue**: Skip to the next iteration
- **return**: Exit the entire function

## Common Patterns

### Iterate over a list
```python
fruits = ["apple", "banana", "cherry"]
for fruit in fruits:
    print(fruit)
```

### Accumulation pattern
```python
total = 0
for num in [1, 2, 3, 4, 5]:
    total += num
print(total)  # 15
```

### Search pattern
```python
numbers = [10, 20, 30, 40]
target = 30
found = False
for num in numbers:
    if num == target:
        found = True
        break
```

## When to Use Loops

- Processing arrays/lists
- Repeated calculations
- Searching and filtering
- Input validation
- Generating sequences

Loops are the foundation for understanding recursion (where a function calls itself) and iteration patterns throughout computer science!
