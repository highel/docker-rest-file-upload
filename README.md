# update
With Docker API 1.20+ you can send tar archive and upload files directly, please use that method on new containers instead https://docs.docker.com/reference/api/docker_remote_api/ 
```
POST /containers/(id)/copy
```

# docker-rest-file-upload
A simple framework which allows to upload files to most running docker containers via Remote API connection hijacking.

This is a simple library to upload files to Docker container from Java via REST api.

usage:

```
try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
				DockerRestFileUpload.uploadFile("http://localhost:4243", "124abcdefg",
						"/var/log/demo.txt")))) {
      
			bw.write("Mabel and Dipper\n"); 
}  
```
        
Upload is implemented via docker exec and exec/start commands. This library basically emulates 
```docker exec -i ubuntu /bin/bash -c 'cat > file' < file``` behaviour

