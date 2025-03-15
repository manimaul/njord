const path = require('path');

config.devServer.proxy = {
     '/v1': 'http://localhost:9000'
}
config.devServer.historyApiFallback = {
     index: 'index.html',
}

// config.devServer.static = {
//      directory: path.join(__dirname, '../../../../common/src/commonMain/resources/static'),
// }
