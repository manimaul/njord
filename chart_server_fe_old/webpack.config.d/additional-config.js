const webpack = require("webpack")

module.exports = env => {
    config.plugins.push(
        new webpack.DefinePlugin({
            NJORD_VERSION: env.njordVersion
        })
    )
    return config
}
