package main

import (
	"encoding/json"
	"io/ioutil"
	"os"
	"fmt"
	"net/http"
	"bytes"
	"time"
	"github.com/joho/godotenv"
)

// TODO: Make ID's ints
var idMap map[string]int
var titleMap map[int]string
var links map[int][]int

func buildTitleMap() {
	fmt.Println("Building title map")
	jsonFile, err := os.Open("pages.json")
	if err != nil {
		fmt.Println(err)
	}
	defer jsonFile.Close()

	byteValue, _ := ioutil.ReadAll(jsonFile)
	json.Unmarshal(byteValue, &titleMap)
}

func buildIdMap() {
	fmt.Println("Building ID map")
	idMap = make(map[string]int)

	for k, v := range titleMap {
        idMap[v] = k
    }
}

func buildLinkMap() {
	fmt.Println("Building link map")
	jsonFile, err := os.Open("links.json")
	if err != nil {
		fmt.Println(err)
	}
	defer jsonFile.Close()

	byteValue, _ := ioutil.ReadAll(jsonFile)
	json.Unmarshal(byteValue, &links)
}

func login(client *http.Client) (string, string) {
	url := "https://api.thewikigame.com/api/v1/auth/login/"

	// TODO: store in .env
	err := godotenv.Load()
	if err != nil {
		panic(err)
	}

	uName := os.Getenv("USERNAME")
	pWord := os.Getenv("PASSWORD")

	jsonStr := []byte(fmt.Sprintf(`{"username": "%s", "password": "%s", "join_group_type": "public"}`, uName, pWord))

	// Create the request
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonStr))
	req.Header.Set("Content-Type", "application/json")

	// Execute request
	resp, err := client.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	// Get the response body
	body, _ := ioutil.ReadAll(resp.Body)

	// Parse to JSON
	jsonResp := make(map[string]interface{})
	json.Unmarshal(body, &jsonResp)

	// Get token and group info
	token := jsonResp["token"].(string)
	group := jsonResp["group_long_code"].(string)

	return token, group
}

func getRound(client *http.Client, token, group string) (time.Time, string, string, string) {
	url := fmt.Sprintf("https://api.thewikigame.com/api/v1/group/%s/current-round/", group)

	// Create the request
	req, err := http.NewRequest("GET", url, nil)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Token %s", token))

	// Execute request
	resp, err := client.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	// Get the response body
	body, _ := ioutil.ReadAll(resp.Body)

	// Parse to JSON
	jsonResp := make(map[string]interface{})
	json.Unmarshal(body, &jsonResp)

	fmt.Println("ROUND:\n", jsonResp)

	// Unpack
	challenge := jsonResp["challenge"].(map[string]interface{})
	goal := challenge["goal_article"].(map[string]interface{})
	start := challenge["start_article"].(map[string]interface{})

	// Get token and group info
	goalPage := goal["link"].(string)
	startPage := start["link"].(string)
	roundPk := fmt.Sprintf("%.0f", jsonResp["pk"].(float64))
	
	// Get round end time
	startTime,_ := time.Parse(time.RFC3339, jsonResp["start_time"].(string))

	return startTime, goalPage, startPage, roundPk
}

func click(client *http.Client, token, group, roundPk, pageName string) (map[string]interface{}, bool){
	url := fmt.Sprintf("https://api.thewikigame.com/api/v1/group/%s/current-game/click/", group)
	jsonStr := []byte(fmt.Sprintf(`{"link":"%s", "roundPk":%s}`, pageName, roundPk))

	// Create the request
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonStr))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Token %s", token))

	resp, err := client.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	body, _ := ioutil.ReadAll(resp.Body)
	jsonResp := make(map[string]interface{})
	json.Unmarshal(body, &jsonResp)

	roundStats, ok := jsonResp["round_stats"].(map[string]interface{})

	return roundStats, ok
}

func fastestPath(startId, goalId int, links map[int][]int, queue []int, visited map[int]bool, steps map[int]int, limit int) ([][]int, []int, map[int]bool, map[int]int){
	var paths [][]int

	var parent int

	for len(queue) > 0 {
		parent, queue = queue[0], queue[1:]  // Pop first element
		if val, ok := links[parent]; ok{
			for _,child := range val {
				// If not visited and not goal page
				if _,ok := visited[child]; !ok && (child != goalId) {
					visited[child] = true
					queue = append(queue, child)
					steps[child] = parent
				}

				if child == goalId {
					path := []int{child}

					for next := parent; next != -1; next = steps[next] {
						path = append(path,next)
					}

					paths = append(paths, path)

					if len(paths) >= limit {
						return paths, queue, visited, steps
					}
				}
			}
		}
	}
	return paths, queue, visited, steps
}

func reverse(a []int) []int {
	for i := len(a)/2-1; i >= 0; i-- {
		opp := len(a)-1-i
		a[i], a[opp] = a[opp], a[i]
	}
	return a
}

func breakAPI(pages []string, idx int) {
	client := &http.Client{}

	token, group := login(client)

	for {
		startTime, goalPage, startPage, roundPk := getRound(client, token, group)
		fmt.Println("New Round:", startPage, "->", goalPage)

		for time.Now().Before(startTime) {}  // Wait for round to start

		// Use direct path
		click(client, token, group, roundPk, startPage)
		click(client, token, group, roundPk, goalPage)

		// Iterate over all paths intermediate pages. Since API doesn't check if pages
		// are actually correct, any page in between the start and goal pages will work
		for time.Now().Before(startTime.Add(time.Second * time.Duration(120))) {
			click(client, token, group, roundPk, startPage)
			click(client, token, group, roundPk, pages[idx])
			roundStats, ok := click(client, token, group, roundPk, goalPage)
			if ok {
				fmt.Println(roundStats["TheBeast"])
			}
			idx++
		}
	}

}	

func playLegit() {
	buildTitleMap()
	buildIdMap()
	buildLinkMap()

	client := &http.Client{}

	token, group := login(client)
	fmt.Println("Token:", token)
	fmt.Println("Group:", group)

	for {
		startTime, goalPage, startPage, roundPk := getRound(client, token, group)
		fmt.Println("New Round:", startPage, "->", goalPage)

		// Get IDs of start and end pages
		startId := idMap[startPage]
		goalId := idMap[goalPage]
	
		// Build queue, visited and steps used by breadth first-search
		queue := []int{startId}
		visited := map[int]bool{startId: true}
		steps := map[int]int{startId: -1}
		limit := 50

		// Initialize paths with a direct click from start to goal. API doesn't
		// actually check if pages are connected so this always works
		var paths = [][]int{{startId,goalId}} 

		// Get a bulk set of paths to start
		if time.Now().Before(startTime) {
			fmt.Println("Round hasn't started yet. Finding initial set of paths.")
			paths, queue, visited, steps = fastestPath(startId, goalId, links, queue, visited, steps, limit)
			fmt.Println("Paths found. Waiting for round to start.")
		}
		
		for time.Now().Before(startTime) {}  // Wait until round starts

		// Follow original set of paths
		fmt.Println("Following initial paths")
		var roundStats map[string]interface{}
		var ok bool
		for _,path := range paths {
			reverse(path)
			for _,id := range path {
				pageName := titleMap[id]
				roundStats, ok = click(client, token, group, roundPk, pageName)
			}
			if ok {
				fmt.Println(roundStats["TheBeast"])
			}
			
		}

		fmt.Println("Initial paths finished. Finding additional paths.")

		// Find and follow one path at a time until the round ends
		limit = 1
		var endStats map[string]interface{}
		for time.Now().Before(startTime.Add(time.Second * time.Duration(120))) {
			paths, queue, visited, steps = fastestPath(startId, goalId, links, queue, visited, steps, limit)
			for _,path := range paths {
				reverse(path)
				for _,id := range path {
					pageName := titleMap[id]
					roundStats, ok = click(client, token, group, roundPk, pageName)
				}
				if ok {
					endStats = roundStats
					fmt.Println(roundStats["TheBeast"])
				}
			}
		} 
		fmt.Println("Round over. Final scores:")
		fmt.Println(endStats, endStats["TheBeast"])
		for k,v := range endStats {
			v := v.(map[string]interface{})
			points,ok := v["points"].(string)
			if !ok {
				fmt.Println("Failed on points for", k, "|", v["points"])
			}
			wins,ok := v["wins"].(string)
			if !ok {
				fmt.Println("Failed on wins for", k, "|", v["wins"])
			}
			fmt.Printf("%s: %s Wins  |  %s Points\n", k, wins, points)
		}
	}
}

func main() {
	playLegit()
}