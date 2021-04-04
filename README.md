# WikiGameBot
Bot for running 

# Getting Started

## Database Setup
- Create a `Data/` directory.
- Download the wikigraph dataset from [here](https://zenodo.org/record/2539424/files/enwiki.wikilink_graph.2018-03-01.csv.gz?download=1) and extract it into the data folder.
- Run `python build_graphs.py`

## Bot Setup
- Add username and password to a `.env` file in the root directory with the following format
```
USERNAME=<YOUR_WIKIGAME_USERNAME>
PASSWORD=<YOUR_WIKIGAME_PASSWORD>
```

## Playing Games
This is the easy part. Just run.
```
go run play.go
```