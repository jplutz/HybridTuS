# Self-Assessment: Loops & Iteration

Test your understanding of different types of loops, iteration patterns, and loop control.

## Questions

### 1. Range Function
What will this code print?
```python
for i in range(3, 8, 2):
    print(i)
```

**Answer:** 3, 5, 7 (starts at 3, goes up to but not including 8, stepping by 2)

### 2. List Iteration
Write a loop that iterates through a list and prints each element.

**Answer:**
```python
fruits = ["apple", "banana", "cherry"]
for fruit in fruits:
    print(fruit)
```

### 3. While Loop Control
What is the purpose of the condition in a while loop? When does it stop?

**Answer:** The condition determines whether the loop continues. The loop stops when the condition becomes False.

### 4. Enumerate
What does the `enumerate()` function do? Give an example.

**Answer:** `enumerate()` adds a counter to an iterable, returning both index and value:
```python
fruits = ["apple", "banana"]
for index, fruit in enumerate(fruits):
    print(f"{index}: {fruit}")
# Output: 0: apple, 1: banana
```

### 5. Loop Accumulation
Write a loop that calculates the sum of numbers 1 through 10.

**Answer:**
```python
total = 0
for i in range(1, 11):
    total += i
print(total)  # 55
```

### 6. Nested Loops
What will this code output?
```python
for i in range(3):
    for j in range(2):
        print(i, j)
```

**Answer:**
```
0 0
0 1
1 0
1 1
2 0
2 1
```

### 7. Loop Efficiency
What is an infinite loop? How can you prevent them?

**Answer:** An infinite loop runs forever because its condition never becomes False. Prevent them by: 1) ensuring the condition will eventually be False, 2) using a `break` statement with a safety condition, 3) carefully checking loop logic.

## Reflection

Consider:
- Can you identify when to use `for` vs `while` loops?
- Do you understand how to iterate through different data structures?
- What patterns do you see in common loop use cases?
