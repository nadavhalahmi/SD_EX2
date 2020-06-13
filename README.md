# CourseApp: Assignment 2

## Authors
* Nadav Halahmi, 206784258
* Adva Bitan, 314628090

## Notes

### Implementation Summary
Now we have 3 main databases: torrentsDB, trackersDB, peersDB, torrentsStatsDB, filesDB
and piecesDB.
torrentsDB holds for each torrent weather the torrent exists or not, it's announce-list and/or announce, and it's peers.
trackersDB holds for each torrent's tracker it's scrape data (stats), or failuere reason if there was a failuere last time trying to connect to it.
peersDB holds for each torrent's peer if it's valid or not, and it's id if it exists.
torrentsStatsDB holds for each loaded torrent it's stats.
filesDB holds the downloaded files.
piecesDB hold for each torrent it's pieces.

### Testing Summary
Functions of courseTorrentApp were tested.

### Difficulties
Many things didnt work and it was hard to get the flow. spended days for simple things we just didnt know how to do in kotlin. Spent lots of time on convert to futures. There was a lack of tests.

### Feedback
Facebook QA was great! Thanks!
