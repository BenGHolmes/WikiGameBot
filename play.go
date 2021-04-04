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
	"sync"
	"strings"
)

// TODO: Make ID's ints
var idMap map[string]int
var titleMap map[int]string
var links map[int][]int
var uName string
var client http.Client
var token string
var group string

func buildTitleIdMaps(wg *sync.WaitGroup) {
	defer wg.Done()

	// Load json file
	jsonFile, _ := os.Open("Data/pages.json")
	defer jsonFile.Close()

	// Unmarshal to titleMap
	byteValue, _ := ioutil.ReadAll(jsonFile)
	json.Unmarshal(byteValue, &titleMap)

	// Invert to get idMap
	idMap = make(map[string]int)
	for k, v := range titleMap {
        idMap[v] = k
    }
}

func buildLinkMap(wg *sync.WaitGroup) {
	defer wg.Done()

	// Load json file
	jsonFile, _ := os.Open("Data/links.json")
	defer jsonFile.Close()

	// Unmarshal to links
	byteValue, _ := ioutil.ReadAll(jsonFile)
	json.Unmarshal(byteValue, &links)
}

func login() {	
	fmt.Println("  Logging in...")

	url := "https://api.thewikigame.com/api/v1/auth/login/"

	// TODO: store in .env
	err := godotenv.Load()
	if err != nil {
		panic(err)
	}

	uName = os.Getenv("USERNAME")
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
	token = jsonResp["token"].(string)
	group = jsonResp["group_long_code"].(string)

	fmt.Println("  Login successful.\n")
}

func getRound() (time.Time, string, string, string) {
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

func click(roundPk, pageName string) error{
	url := fmt.Sprintf("https://api.thewikigame.com/api/v1/group/%s/current-game/click/", group)
	jsonStr := []byte(fmt.Sprintf(`{"link":"%s", "roundPk":%s}`, pageName, roundPk))

	// Create the request
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonStr))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Token %s", token))

	_, err = client.Do(req)
	if err != nil {
		return err
	}

	return nil
}

func pathFinder(startId, goalId int, links map[int][]int, ch chan []int, timeout time.Time) {
	// Build queue, visited and steps used by breadth first-search
	queue := []int{startId}
	visited := map[int]bool{startId: true}
	steps := map[int]int{startId: -1}
	var parent int

	for len(queue) > 0 {
		if time.Now().After(timeout) {
			fmt.Println("Path finder exiting")
			return
		}
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
					// Start at goalId
					path := []int{child}

					// Work backwards to startId
					for next := parent; next != -1; next = steps[next] {
						path = append(path,next)
					}

					// Send this path to the channel
					ch <- path
				}
			}
		}
	}
}

func printScores(fromPage, toPage string) {
	scoreClient := http.Client{} // Define new client so we don't bog down main thread

	url := fmt.Sprintf("https://api.thewikigame.com/api/v1/group/%s/round-results/?page=1&active=false", group)
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Token %s", token))

	var stats map[string]interface{}

	// Loop until we get a response. This is to deal with cases where we call this before
	// the API has a chance to register the end of the game. This way we always see results.
	for stats == nil {
		resp, _ := scoreClient.Do(req)
		defer resp.Body.Close()
	
		// Get the response body
		body, _ := ioutil.ReadAll(resp.Body)
	
		// Parse to JSON
		jsonResp := make(map[string]interface{})
		json.Unmarshal(body, &jsonResp)
	
		// Parse points and print
		results := jsonResp["results"].([]interface{})[0].(map[string]interface{})
		stats = results["stats"].(map[string]interface{})
	}

	fmt.Printf("Results for %s -> %s:\n", fromPage, toPage)
	for k,v := range stats {
		name :=	strings.Split(k,"_")[0]
		score := v.(map[string]interface{})
		fmt.Printf("%3.0f wins | %7.0f points | %s\n", score["wins"].(float64), score["points"].(float64), name)
	}
}

func reverse(a []int) []int {
	for i := len(a)/2-1; i >= 0; i-- {
		opp := len(a)-1-i
		a[i], a[opp] = a[opp], a[i]
	}
	return a
}

func buildMaps() {
	// Init waitgroup for building our maps
	var wg sync.WaitGroup
	wg.Add(2)

	// Build maps from Title -> ID and ID -> Title in parallel
	// with map of page links.
	fmt.Println("Intial Setup:")
	fmt.Println("  Building maps...")
	go buildTitleIdMaps(&wg)
	go buildLinkMap(&wg)

	// Wait for all maps to finish
	wg.Wait()

	fmt.Println("  Maps done.")
}


func playRound(startPage, goalPage, roundPk string, startTime time.Time) {
	// Get IDs of start and end pages
	startId := idMap[startPage]
	goalId := idMap[goalPage]

	// Make channel for sending paths
	ch := make(chan []int, 100)

	// Start path finder with a timeout at 120 seconds after start time. This is when the round ends
	go pathFinder(startId, goalId, links, ch, startTime.Add(time.Second * time.Duration(120)))
	
	// Wait until round starts before following paths
	for time.Now().Before(startTime) {}

	// Follow paths until the round ends.
	for time.Now().Before(startTime.Add(time.Second * time.Duration(120))) {
		path := <- ch  // Get path from channel
		reverse(path)  // Reverse to go from startPage to goalPage
		for _,id := range path {
			pageName := titleMap[id]
			err := click(roundPk, pageName)
			if err != nil {
				break // If we error for some reason, just move on to next path
			}
		}
	}
	
	// Print scores
	go printScores(startPage, goalPage)

	// Empty channel
	for len(ch) > 0 {
		<- ch
	}
	fmt.Println("Channel empty")
}


func main() {
	// Build maps from json files
	buildMaps()

	client = http.Client{}
	login()

	for {
		startTime, goalPage, startPage, roundPk := getRound()
		fmt.Println("New Round:", startPage, "->", goalPage)

		playRound(startPage, goalPage, roundPk, startTime)

		fmt.Println("Round over.")
	}
}