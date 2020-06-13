# CourseApp: Assignment 1

## Authors
* Nadav Halahmi, 206784258
* Adva Bitan, 314628090

## Notes

### Implementation Summary
Now we have 3 main databases: torrentsDB, trackersDB and peersDB.
torrentsDB holds for each torrent weather the torrent exists or not, it's announce-list and/or announce, and it's peers.
trackersDB holds for each torrent's tracker it's scrape data (stats), or failuere reason if there was a failuere last time trying to connect to it.
peersDB holds for each torrent's peer if it's valid or not.

### Testing Summary
Each function of courseTorrentApp was tested.

### Difficulties
Getting used to guice and using mocks with guice was hard.

### Feedback
Facebook QA was great! Thanks!
