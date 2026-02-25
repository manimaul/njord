const path = require('path');

config.devServer.proxy = [
     {
          context: ["/v1"],
          target: "http://localhost:9000",
     },
]

config.devServer.historyApiFallback = {
     index: 'index.html',
}

config.devServer.static = {
     directory: path.join(__dirname, '../../../../web/src/jsMain/resources'),
}