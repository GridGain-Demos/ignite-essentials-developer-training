SELECT track.trackid, track.name, genre.name, MAX(milliseconds / (1000 * 60)) as duration FROM track
JOIN genre ON track.genreId = genre.genreId
WHERE track.genreId < 17
GROUP BY track.trackid, track.name, genre.name ORDER BY duration DESC LIMIT 20;
