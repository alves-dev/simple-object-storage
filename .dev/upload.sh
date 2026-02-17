# 1. Upload arquivo privado
curl -X POST http://localhost:8080/api/files/upload \
  -H "X-API-Key: key1" \
  -F "file=@upload.sh" \
  -F "bucket=confidential" \
  -F "isPublic=false"

# Response:
#{
#   "success":true,
#   "data":{
#      "fileId":"dc48c3b5119a470083682f402ec3db54",
#      "bucket":"confidential",
#      "filename":"upload.sh",
#      "url":"http://localhost:8080/files/dc48c3b5119a470083682f402ec3db54",
#      "isPublic":false,
#      "accessKey":"pr09xf3ypk1ttst2",
#      "privateUrl":"http://localhost:8080/files/dc48c3b5119a470083682f402ec3db54?key=pr09xf3ypk1ttst2",
#      "fileSize":1033,
#      "mimeType":"application/octet-stream",
#      "uploadedAt":"2026-02-17T11:15:29.894915508Z"
#   },
#   "message":null,
#   "error":null
#}

# 2. Acessar arquivo privado
curl "http://localhost:8080/files/dc48c3b5119a470083682f402ec3db54?key=pr09xf3ypk1ttst2" --output downloaded.sh

# 3. Upload arquivo público
curl -X POST http://localhost:8080/api/files/upload \
  -H "X-API-Key: key1" \
  -F "file=@image.jpg" \
  -F "bucket=images" \
  -F "isPublic=true"

curl -X POST http://localhost:8080/api/files/upload \
  -H "X-API-Key: key1" \
  -F "file=@../.docs/doc.md" \
  -F "bucket=documents" \
  -F "filename=document.md" \
  -F "isPublic=true" \
  -F 'metadata={"uploadedBy":"user@example.com"}'

# 4. Acessar arquivo público (sem API Key necessária)
curl "http://localhost:8080/files/def456" --output downloaded.jpg

# 5. Deletar arquivo (requer API Key)
curl -X DELETE http://localhost:8080/api/files/2d652bf64be94d338ca1c65a26a87573 \
  -H "X-API-Key: key1"