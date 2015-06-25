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
