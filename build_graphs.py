import json

# Map from ID to Title 
pages = {}

# Map from ID to ID
links = {}

with open('Data/enwiki.wikilink_graph.2018-03-01.csv') as f:
    f.readline()  # Skip header line
    i = 0
    while True:
        i += 1
        if i%10000==0:
            print(f'\r{i} links done.', end='')
        # Get next line from file
        line = f.readline()
        
        # If line empty, break
        if not line:
            break
        
        # Split line into components
        arr = line.strip().split('\t')
        
        # Skip if we don't have correct format
        if len(arr) < 4:
            continue
            
        try:
            from_id, from_title, to_id, to_title = arr
        except:
            print(arr)
            
        from_title = from_title.replace(" ", "_")
        to_title = to_title.replace(" ", "_")
        from_id = int(from_id)
        to_id = int(to_id)
        
        if from_id not in pages:
            pages[from_id] = from_title
        if to_id not in pages:
            pages[to_id] = to_title
            
        if from_id not in links:
            links[from_id] = [to_id]
        else:
            links[from_id].append(to_id)
    print()
            
print(len(pages), "total pages.")

with open('Data/pages.json', 'w') as f:
    f.write(json.dumps(pages))
    
with open('Data/links.json', 'w') as f:
    f.write(json.dumps(links))