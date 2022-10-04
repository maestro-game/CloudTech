<!doctype html>
<html>
<head>
    <meta charset="utf-8" />
    <title>Фотоархив</title>
</head>
<body>
<h1>Фотоархив</h1>
<ul>
    <#list albums as album>
        <li><a href="album${album_index}.html">${album}</a></li>
    </#list>
</ul>
</body>