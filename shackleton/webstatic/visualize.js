import * as d3 from 'd3';

console.log(d3);

export default sampleFile => {
    d3.json(sampleFile).then(data => {
        // cleanup java's millisecond timestamps
        data.nodes = data.nodes.map(n => {n.timestamp /= 1000; return n})

        let container = d3.select("body");
        let w = () => container.node().clientWidth;
        let h = () => container.node().clientHeight;
        let svg = container.append("svg");
        let g = svg.append("g").attr("class", "graph");


    // title
        g.append("g").attr("class", "title")
            .append("text")
            .text("Blocks by Shard over Time")
            .attr("transform", `translate(${w() / 2}, ${h() * 0.05})`)
            .attr("fill", "currentColor")
            .style("text-anchor", "middle")
            .style("font-size", "125%")
            .style("text-decoration", "underline")


    // scale x
        let min_timestamp = Math.min(...data.nodes.map(d => d.timestamp));
        let max_timestamp = Math.max(...data.nodes.map(d => d.timestamp));
        let x = d3.scaleLinear().domain([min_timestamp, max_timestamp]).range([150, w() - 100]);
        let axis_x = d3.axisBottom(x)
        let axis_x_draw = g.append("g").attr("class", "scale_x")
            .attr("transform", `translate(0, ${h() - h() * 0.05})`)
            .call(axis_x)
        axis_x_draw.append("text").text("Timestamp")
                .attr("transform", `translate(${w() / 2}, ${h() * 0.033})`)
                .attr("fill", "currentColor")
                .style("text-anchor", "middle")


    // scale y
        let max_shard = Math.max(...data.nodes.map(d => d.shard));
        // let color_index = (i) => `hsl(${360 / max_shard * i}, 100%, 50%)`;
        let color_index = i => d3.interpolateRainbow((1/max_shard) * i)
        let y = d3.scaleLinear().domain([max_shard, 0]).range([100, h() - 100]);
        let axis_y = d3.axisLeft(y)
            .tickValues([...Array(max_shard).keys()].map(i => i+1))
            .tickFormat(d3.format('d'));
        let axis_y_draw = g.append("g").attr("class", "scale_y")
            .attr("transform", `translate(${w() * 0.05}, 0)`)
            .call(axis_y)
        axis_x_draw.append("text").attr("transform", `translate(${-w() * 0.033}, ${h() / 2})`)
            .attr("fill", "currentColor")
            .style("text-anchor", "middle")
            .text("Shard")


    // gridlines x
        let gridlines_x = g.append("g").attr("class", "gridlines_X")
            .selectAll("line").data(data.nodes).enter()
            .append("line")
            .attr("x1", n => x(n.timestamp))
            .attr("y1", n => y(n.shard))
            .attr("x2", n => x(n.timestamp))
            .attr("y2", h() - h() * 0.05)
            .style("stroke", "white")
            .style("stroke-opacity", 0.1)
            .style("stroke-dasharray", "1")


        // links
        let n = (i) => data.nodes[i - 1]
        let color_ref = (l) => `s${l.source}t${l.target}`
        let links = g.append("g").attr("class", "links").selectAll("line").data(data.links).enter()
            .append("line")
                .attr("x1", l => x(n(l.source).timestamp))
                .attr("y1", l => y(n(l.source).shard))
                .attr("x2", l => x(n(l.target).timestamp))
                .attr("y2", l => y(n(l.target).shard))
                .style("stroke-width", 2)
                .style("stroke-opacity", 0.5)
                .style("stroke", l => `url(#${color_ref(l)})`)
                .on("mouseover", (event, link) => {
                    d3.select(event.target)
                        .style("stroke", "white")
                        .style("stroke-opacity", 1)
                })
                .on("mouseout", (event, d) => {
                    let link_doms = d3.select(event.target)
                    link_doms
                        .style("stroke", l => `url(#${color_ref(l)})`)
                        .style("stroke-opacity", 0.5)
                })

        // gradients
        let gradients = g.select(".links").append("defs").selectAll("linearGradient").data(data.links).enter()
            .append("linearGradient")
            .attr("id", l => color_ref(l))
            .attr("x1", l => x(n(l.source).timestamp))
            .attr("y1", l => y(n(l.source).shard))
            .attr("x2", l => x(n(l.target).timestamp))
            .attr("y2", l => y(n(l.target).shard))
            .attr("gradientUnits", "userSpaceOnUse")
            .call(selection => {
                selection.append("stop").attr("stop-color", l => color_index(n(l.source).shard)).attr("offset", 0)
                selection.append("stop").attr("stop-color", l => color_index(n(l.target).shard)).attr("offset", 1)
            })

    // block info
        let info = container.append("div").attr("class", "info")
            .style("position", "absolute")
            .style("padding", "1em")
            .style("color", "white")
            .style("text-anchor", "middle")
            .text("<click_node_to_get_data>")


    // nodes
        let nodes = g.append("g").attr("class", "nodes")
            .selectAll("circle").data(data.nodes).enter()
            .append("circle")
                .attr("cx", n => x(n.timestamp))
                .attr("cy", n => y(n.shard))
                .style("fill", n => color_index(n.shard))
                .style("r", 4)
                .style("stroke", "#ffffffaf")
                .on("mouseover", (event, node) => {
                    d3.select(event.target)
                        .style("fill", "white")
                        .style("stroke", "black")

                    links
                        .style("stroke", l => (l.source === node.id) ? "white" : `url(#${color_ref(l)})`)
                        .style("stroke-opacity", l => (l.source === node.id) ? 1 : 0.5)
                        .style("z-index", 1)
                        .sort(l => l.source === node.id ? 1 : -1)

                })
                .on("mouseout", (event, d) => {
                    d3.select(event.target)
                        .style("fill", a => color_index(a.shard))
                        .style("stroke", "white")
                    links
                        .style("stroke", l => `url(#${color_ref(l)})`)
                        .style("stroke-opacity", 0.5)
                })
                .on("click", (event, d) => {
                    info.text(JSON.stringify(d))
                })

        function resizeViewBox() {
            svg.attr("viewBox", `0 0 ${w()} ${h()}`);
            svg.call(d3.zoom().extent([[0, 0], [w(), h()]]).scaleExtent([0.25, 100])
                .on("zoom", ({transform}) => {
                    nx = transform.rescaleX(x)
                    ny = transform.rescaleY(y)
                    let n = (i) => data.nodes[i - 1]
                    links
                        .attr("x1", d => nx(n(d.source).timestamp))
                        .attr("y1", d => ny(n(d.source).shard))
                        .attr("x2", d => nx(n(d.target).timestamp))
                        .attr("y2", d => ny(n(d.target).shard))
                    gradients
                        .attr("x1", d => nx(n(d.source).timestamp))
                        .attr("y1", d => ny(n(d.source).shard))
                        .attr("x2", d => nx(n(d.target).timestamp))
                        .attr("y2", d => ny(n(d.target).shard))
                    nodes
                        .attr("cx", d => nx(d.timestamp))
                        .attr("cy", d => ny(d.shard))
                    gridlines_x
                        .attr("x1", n => nx(n.timestamp))
                        .attr("y1", n => ny(n.shard))
                        .attr("x2", n => nx(n.timestamp))
                        .attr("y2", h() - h() * 0.05)

                    axis_x.scale(nx)
                    axis_x_draw
                        .call(axis_x)
                        .attr("transform", `translate(0, ${h() - h() * 0.05})`)
                    axis_y.scale(ny)
                    axis_y_draw.call(axis_y)

                    // g.attr("transform", transform)
                }));
        }

        resizeViewBox();
        window.addEventListener('resize', resizeViewBox);

    });
}