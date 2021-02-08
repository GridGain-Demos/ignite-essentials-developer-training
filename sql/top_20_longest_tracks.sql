SELECT trackid, name, MAX(milliseconds / (1000 * 60)) as duration FROM track
WHERE genreId < 17
GROUP BY trackid, name ORDER BY duration DESC LIMIT 20;
