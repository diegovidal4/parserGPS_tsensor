#!/bin/bash

echo "Deploy to Trabajando..."
scp -r dist/GpsTaipParser.jar root@190.54.34.35:/home/gps/lib/parsertaip
echo "Done!"
