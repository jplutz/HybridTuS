# Loops Example - For Loop

## Counting from 1 to 10

A classic example demonstrating basic for loop syntax:

```python
for i in range(1, 11):
    print(i)
```

**Output:**
```
1
2
3
4
5
6
7
8
9
10
```

## Sum of Numbers

Calculate the sum of numbers 1 through 100:

```python
total = 0
for i in range(1, 101):
    total += i
print(f"Sum: {total}")  # Sum: 5050
```

## Iterating Over Lists

Process each item in a collection:

```python
students = ["Alice", "Bob", "Charlie", "Diana"]

for student in students:
    print(f"Hello, {student}!")
```

**Output:**
```
Hello, Alice!
Hello, Bob!
Hello, Charlie!
Hello, Diana!
```

## Nested Loops

Loops within loops for 2D data:

```python
for i in range(1, 4):
    for j in range(1, 4):
        print(f"({i},{j})", end=" ")
    print()  # New line
```

**Output:**
```
(1,1) (1,2) (1,3)
(2,1) (2,2) (2,3)
(3,1) (3,2) (3,3)
```

## Try It Yourself

1. Write a loop to print all even numbers from 0 to 20
2. Calculate the product of numbers 1 through 5
3. Find the maximum value in a list: `[45, 12, 78, 34, 89, 23]`
