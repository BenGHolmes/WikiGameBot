package main

import (
	"testing"
	"fmt"
)

func TestFastestPaths(t *testing.T) {
	var links = map[int][]int{
		0: {1,2,3,4,5,6},
		2: {5},
		3: {5},
		4: {5,6},
		5: {7, 8, 9},
		6: {5},
	}

	paths := fastestPath(0, 1, links)
	fmt.Println(paths)
}