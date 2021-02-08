SELECT track.trackId, track.name as track_name, genre.name as genre, artist.name as artist,
MAX(milliseconds / (1000 * 60)) as duration FROM track
LEFT JOIN artist ON track.artistId = artist.artistId
JOIN genre ON track.genreId = genre.genreId
WHERE track.genreId < 17
GROUP BY track.trackId, track.name, genre.name, artist.name ORDER BY duration DESC LIMIT 20;
