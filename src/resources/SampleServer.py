import time

from flask import Flask

app = Flask(__name__)

count = 0

@app.route('/<reqId>')
def hello_world(reqId):
    global count
    print("Processing "+str(reqId)+" th Request")
    delay = int(open("./delay.txt",'r').read())
    time.sleep(delay)
    count += 1
    return '<h1>'+str(count)+'</h1>'

@app.route('/reset')
def reset_count():
    global count
    count = 0

if __name__ == '__main__':
    app.run()